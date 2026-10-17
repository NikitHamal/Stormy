package com.oro.app.data.ai.checkpoint

import com.oro.app.data.ai.AiHttp
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable
data class CheckpointFile(
    /** Project-relative path. */
    val path: String,
    /** False when the file did not exist before the run (restore deletes it). */
    val existed: Boolean,
    /** Name of the saved pre-image inside the checkpoint folder. */
    val blob: String? = null
)

@Serializable
data class CheckpointManifest(
    val id: String,
    val sessionId: String,
    /** The user message whose run this checkpoint precedes. */
    val messageId: String,
    val createdAt: Long,
    val label: String,
    val files: List<CheckpointFile>
)

/**
 * Per-run file snapshots, so everything Orobi changed after a message can be
 * put back exactly as it was.
 *
 * Copy-on-first-write: nothing is copied up front. The first time a run is
 * about to change a path, its current bytes (or the fact that it did not
 * exist) are saved; later changes to the same path in that run cost nothing.
 * A run that only reads leaves no checkpoint at all. Stored in the project
 * under `.oro/checkpoints/<id>/`, which the agent's path policy keeps it out of.
 */
class Checkpoints(private val projectRoot: File) {

    private val base get() = File(projectRoot, ".oro/checkpoints")

    /** Records one run. Thread-safe: parallel tool calls may capture at once. */
    inner class Recorder internal constructor(
        private val id: String,
        private val sessionId: String,
        private val messageId: String,
        private val label: String
    ) {
        private val dir = File(base, id)
        private val captured = LinkedHashMap<String, CheckpointFile>()
        private var bytes = 0L

        /** Save [relativePath]'s current state if this run has not touched it yet. */
        @Synchronized
        fun capture(relativePath: String) {
            val rel = normalize(relativePath) ?: return
            if (rel in captured || rel.startsWith(".oro/") || rel.startsWith(".git/")) return
            val f = File(projectRoot, rel)
            when {
                f.isDirectory -> f.walkTopDown().filter { it.isFile }.take(MAX_FILES).forEach { child ->
                    capture(child.relativeTo(projectRoot).invariantSeparatorsPath)
                }
                f.isFile -> {
                    if (captured.size >= MAX_FILES || bytes + f.length() > MAX_BYTES) return
                    val blob = "f${captured.size}"
                    dir.mkdirs()
                    f.copyTo(File(dir, blob), overwrite = true)
                    bytes += f.length()
                    captured[rel] = CheckpointFile(rel, existed = true, blob = blob)
                }
                else -> captured[rel] = CheckpointFile(rel, existed = false)
            }
        }

        /** Paths this run has changed so far (existing or deleted). */
        @Synchronized
        fun changedPaths(): List<String> = captured.keys.toList()

        /** Write the manifest; returns null when the run changed nothing. */
        @Synchronized
        fun finish(): CheckpointManifest? {
            if (captured.isEmpty()) return null
            val m = CheckpointManifest(id, sessionId, messageId, System.currentTimeMillis(), label, captured.values.toList())
            dir.mkdirs()
            File(dir, MANIFEST).writeText(AiHttp.json.encodeToString(CheckpointManifest.serializer(), m))
            prune()
            return m
        }
    }

    fun begin(sessionId: String, messageId: String, label: String): Recorder =
        Recorder("${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}", sessionId, messageId, label.take(80))

    /** Checkpoints of one session, newest first. */
    fun list(sessionId: String? = null): List<CheckpointManifest> =
        (base.listFiles()?.toList().orEmpty())
            .mapNotNull { d ->
                runCatching {
                    AiHttp.json.decodeFromString(CheckpointManifest.serializer(), File(d, MANIFEST).readText())
                }.getOrNull()
            }
            .filter { sessionId == null || it.sessionId == sessionId }
            .sortedByDescending { it.createdAt }

    data class RestoreResult(val checkpoints: Int, val files: List<String>)

    /**
     * Undo every run of [sessionId] from the one that followed [messageId]
     * onwards: newest first, so each path ends at its oldest saved state.
     */
    fun restoreFrom(sessionId: String, messageId: String): RestoreResult {
        val all = list(sessionId)
        val start = all.lastOrNull { it.messageId == messageId } ?: return RestoreResult(0, emptyList())
        val toUndo = all.filter { it.createdAt >= start.createdAt }
        val touched = LinkedHashSet<String>()
        for (cp in toUndo) {
            val dir = File(base, cp.id)
            for (file in cp.files) {
                val target = File(projectRoot, file.path)
                if (file.existed && file.blob != null) {
                    target.parentFile?.mkdirs()
                    File(dir, file.blob).copyTo(target, overwrite = true)
                } else if (target.isFile) {
                    target.delete()
                }
                touched += file.path
            }
            dir.deleteRecursively()
        }
        return RestoreResult(toUndo.size, touched.toList())
    }

    /** A file's saved pre-image as text, or null if it did not exist before (or is binary/missing). */
    fun before(cp: CheckpointManifest, file: CheckpointFile): String? {
        if (!file.existed || file.blob == null) return null
        val f = File(File(base, cp.id), file.blob)
        return if (f.isFile && f.length() < 2_000_000) runCatching { f.readText() }.getOrNull() else null
    }

    /** Put one file back to its state in [cp], leaving the rest of the project alone. */
    fun restoreFile(cp: CheckpointManifest, file: CheckpointFile) {
        val target = File(projectRoot, file.path)
        if (file.existed && file.blob != null) {
            target.parentFile?.mkdirs()
            File(File(base, cp.id), file.blob).copyTo(target, overwrite = true)
        } else if (target.isFile) target.delete()
    }

    /** Message ids that have a checkpoint, for showing the restore action. */
    fun messageIds(sessionId: String): Set<String> = list(sessionId).map { it.messageId }.toSet()

    private fun prune() {
        list().drop(MAX_CHECKPOINTS).forEach { File(base, it.id).deleteRecursively() }
    }

    private fun normalize(path: String): String? {
        val p = path.trim().replace('\\', '/').trimStart('/').removePrefix("./")
        if (p.isEmpty() || p.split('/').any { it == ".." }) return null
        return p
    }

    companion object {
        private const val MANIFEST = "manifest.json"
        private const val MAX_FILES = 400
        private const val MAX_BYTES = 40L * 1024 * 1024
        private const val MAX_CHECKPOINTS = 60
    }
}
