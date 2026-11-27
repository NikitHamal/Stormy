package com.oro.app.ui.screens.preview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.staticCompositionLocalOf
import com.oro.app.data.preview.ArtifactAction
import com.oro.app.data.preview.ArtifactKind
import com.oro.app.data.preview.PreviewArtifacts
import com.oro.app.ui.screens.pdf.PdfViewerActivity
import java.io.File

/**
 * Opens a project file in the viewer that suits it: the live preview for pages,
 * styles, scripts, SVGs and Markdown, the built-in viewer for PDFs.
 *
 * Provided by the editor screen (it knows the project); null where there is no
 * project, so a card shown elsewhere simply has no button.
 */
val LocalArtifactOpener = staticCompositionLocalOf<((String) -> Unit)?> { null }

object ArtifactLauncher {

    /** The button label a file earns ("Preview", "View PDF"), or null if it earns none. */
    fun label(path: String?): String? = when (path?.let(PreviewArtifacts::actionFor)) {
        ArtifactAction.PDF_VIEWER -> "View PDF"
        ArtifactAction.LIVE_PREVIEW -> "Preview"
        else -> null
    }

    /** Tools whose result is a file the user may want to see; [path] is what the call touched. */
    fun producesFile(toolName: String): Boolean = toolName in FILE_TOOLS

    fun open(context: Context, projectId: String, projectPath: String, relativePath: String) {
        val clean = relativePath.replace('\\', '/').trimStart('/')
        when (PreviewArtifacts.actionFor(clean)) {
            ArtifactAction.PDF_VIEWER -> {
                val file = File(projectPath, clean)
                if (file.isFile) PdfViewerActivity.open(context, file.absolutePath, file.name)
                else Toast.makeText(context, "$clean isn't there any more", Toast.LENGTH_SHORT).show()
            }
            ArtifactAction.LIVE_PREVIEW -> {
                val intent = Intent(context, PreviewActivity::class.java).apply {
                    putExtra(PreviewActivity.EXTRA_PROJECT_ID, projectId)
                    putExtra(PreviewActivity.EXTRA_PROJECT_PATH, projectPath)
                    // Styles and scripts belong to a page: open the project's own page and
                    // watch it change, rather than show the raw file in the WebView.
                    val own = PreviewArtifacts.kindOf(clean)
                    if (own == ArtifactKind.WEB_PAGE || own == ArtifactKind.SVG || own == ArtifactKind.MARKDOWN) {
                        putExtra(PreviewActivity.EXTRA_START_PATH, clean)
                    }
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            ArtifactAction.NONE -> Unit
        }
    }

    private val FILE_TOOLS = setOf(
        "write_file", "create_file", "edit_file", "search_replace", "patch_file", "apply_patch", "append_file",
        "write", "create", "edit", "replace", "patch", "append"
    )
}
