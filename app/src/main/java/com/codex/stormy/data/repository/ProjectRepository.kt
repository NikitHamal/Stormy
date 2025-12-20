package com.codex.stormy.data.repository

import android.content.Context
import com.codex.stormy.data.local.dao.ProjectDao
import com.codex.stormy.data.local.entity.ProjectEntity
import com.codex.stormy.data.local.entity.ProjectTemplate
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val context: Context
) {
    private val projectsBaseDir: File
        get() = File(context.filesDir, "projects")

    fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { Project.fromEntity(it) }
        }
    }

    fun searchProjects(query: String): Flow<List<Project>> {
        return projectDao.searchProjects(query).map { entities ->
            entities.map { Project.fromEntity(it) }
        }
    }

    suspend fun getProjectById(projectId: String): Project? {
        return projectDao.getProjectById(projectId)?.let { Project.fromEntity(it) }
    }

    fun observeProjectById(projectId: String): Flow<Project?> {
        return projectDao.observeProjectById(projectId).map { entity ->
            entity?.let { Project.fromEntity(it) }
        }
    }

    suspend fun createProject(
        name: String,
        description: String,
        template: ProjectTemplate
    ): Result<Project> = withContext(Dispatchers.IO) {
        try {
            if (projectDao.countProjectsByName(name) > 0) {
                return@withContext Result.failure(ProjectExistsException(name))
            }

            val projectId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val projectDir = File(projectsBaseDir, projectId)

            if (!projectDir.mkdirs()) {
                return@withContext Result.failure(ProjectCreationException("Failed to create project directory"))
            }

            createTemplateFiles(projectDir, template)

            val project = Project(
                id = projectId,
                name = name,
                description = description,
                template = template,
                createdAt = timestamp,
                updatedAt = timestamp,
                lastOpenedAt = timestamp,
                thumbnailPath = null,
                rootPath = projectDir.absolutePath
            )

            projectDao.insertProject(project.toEntity())
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
            if (project != null) {
                val projectDir = File(project.rootPath)
                if (projectDir.exists()) {
                    projectDir.deleteRecursively()
                }
                projectDao.deleteProjectById(projectId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLastOpenedAt(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.updateLastOpenedAt(projectId, System.currentTimeMillis())
    }

    /**
     * Create an empty project directory for Git clone operations.
     * Unlike createProject, this doesn't create any template files.
     */
    suspend fun createProjectForClone(name: String): Result<Project> = withContext(Dispatchers.IO) {
        try {
            if (projectDao.countProjectsByName(name) > 0) {
                return@withContext Result.failure(ProjectExistsException(name))
            }

            val projectId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val projectDir = File(projectsBaseDir, projectId)

            if (!projectDir.mkdirs()) {
                return@withContext Result.failure(ProjectCreationException("Failed to create project directory"))
            }

            val project = Project(
                id = projectId,
                name = name,
                description = "Cloned from Git repository",
                template = ProjectTemplate.BLANK,
                createdAt = timestamp,
                updatedAt = timestamp,
                lastOpenedAt = timestamp,
                thumbnailPath = null,
                rootPath = projectDir.absolutePath
            )

            projectDao.insertProject(project.toEntity())
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLastUsedModelId(projectId: String, modelId: String?) = withContext(Dispatchers.IO) {
        projectDao.updateLastUsedModelId(projectId, modelId)
    }

    suspend fun getFileTree(projectId: String): List<FileTreeNode> = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectById(projectId) ?: return@withContext emptyList()
        val rootDir = File(project.rootPath)

        if (!rootDir.exists() || !rootDir.isDirectory) {
            return@withContext emptyList()
        }

        buildFileTree(rootDir, project.rootPath, 0)
    }

    private fun buildFileTree(directory: File, basePath: String, depth: Int): List<FileTreeNode> {
        val files = directory.listFiles() ?: return emptyList()

        return files
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { file ->
                if (file.isDirectory) {
                    FileTreeNode.FolderNode(
                        name = file.name,
                        path = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar),
                        depth = depth,
                        isExpanded = false,
                        children = buildFileTree(file, basePath, depth + 1)
                    )
                } else {
                    FileTreeNode.FileNode(
                        name = file.name,
                        path = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar),
                        depth = depth,
                        extension = file.extension.lowercase(),
                        size = file.length()
                    )
                }
            }
    }

    suspend fun readFile(projectId: String, relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val file = File(project.rootPath, relativePath)
            if (!file.exists()) {
                return@withContext Result.failure(FileNotFoundException(relativePath))
            }

            Result.success(file.readText())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(
        projectId: String,
        relativePath: String,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val file = File(project.rootPath, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(
        projectId: String,
        relativePath: String,
        content: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val file = File(project.rootPath, relativePath)
            if (file.exists()) {
                return@withContext Result.failure(FileExistsException(relativePath))
            }

            file.parentFile?.mkdirs()
            file.writeText(content)

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(projectId: String, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val folder = File(project.rootPath, relativePath)
            if (folder.exists()) {
                return@withContext Result.failure(FileExistsException(relativePath))
            }

            if (!folder.mkdirs()) {
                return@withContext Result.failure(FolderCreationException(relativePath))
            }

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(projectId: String, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val file = File(project.rootPath, relativePath)
            if (!file.exists()) {
                return@withContext Result.failure(FileNotFoundException(relativePath))
            }

            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (!deleted) {
                return@withContext Result.failure(FileDeletionException(relativePath))
            }

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(
        projectId: String,
        oldPath: String,
        newPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val oldFile = File(project.rootPath, oldPath)
            val newFile = File(project.rootPath, newPath)

            if (!oldFile.exists()) {
                return@withContext Result.failure(FileNotFoundException(oldPath))
            }

            if (newFile.exists()) {
                return@withContext Result.failure(FileExistsException(newPath))
            }

            newFile.parentFile?.mkdirs()
            if (!oldFile.renameTo(newFile)) {
                return@withContext Result.failure(FileRenameException(oldPath, newPath))
            }

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyFile(
        projectId: String,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val sourceFile = File(project.rootPath, sourcePath)
            val destFile = File(project.rootPath, destinationPath)

            if (!sourceFile.exists()) {
                return@withContext Result.failure(FileNotFoundException(sourcePath))
            }

            if (destFile.exists()) {
                return@withContext Result.failure(FileExistsException(destinationPath))
            }

            destFile.parentFile?.mkdirs()

            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(destFile, overwrite = false)
            } else {
                sourceFile.copyTo(destFile, overwrite = false)
            }

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveFile(
        projectId: String,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val sourceFile = File(project.rootPath, sourcePath)
            val destFile = File(project.rootPath, destinationPath)

            if (!sourceFile.exists()) {
                return@withContext Result.failure(FileNotFoundException(sourcePath))
            }

            if (destFile.exists()) {
                return@withContext Result.failure(FileExistsException(destinationPath))
            }

            destFile.parentFile?.mkdirs()

            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(destFile, overwrite = false)
                sourceFile.deleteRecursively()
            } else {
                sourceFile.copyTo(destFile, overwrite = false)
                sourceFile.delete()
            }

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAndReplace(
        projectId: String,
        search: String,
        replace: String,
        filePattern: String? = null,
        dryRun: Boolean = false
    ): Result<SearchReplaceResult> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val rootDir = File(project.rootPath)
            val result = SearchReplaceResult()

            searchAndReplaceInDir(rootDir, project.rootPath, search, replace, filePattern, dryRun, result)

            if (!dryRun && result.filesModified > 0) {
                projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchAndReplaceInDir(
        directory: File,
        basePath: String,
        search: String,
        replace: String,
        filePattern: String?,
        dryRun: Boolean,
        result: SearchReplaceResult
    ) {
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.name.startsWith(".")) continue

            if (file.isDirectory) {
                searchAndReplaceInDir(file, basePath, search, replace, filePattern, dryRun, result)
            } else {
                if (filePattern != null && !matchesPattern(file.name, filePattern)) {
                    continue
                }

                try {
                    val content = file.readText()
                    if (content.contains(search)) {
                        val relativePath = file.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)
                        val occurrences = content.split(search).size - 1

                        result.filesModified++
                        result.totalReplacements += occurrences
                        result.files.add(SearchReplaceFileResult(relativePath, occurrences))

                        if (!dryRun) {
                            val newContent = content.replace(search, replace)
                            file.writeText(newContent)
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read as text
                }
            }
        }
    }

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(filename)
    }

    suspend fun patchFile(
        projectId: String,
        path: String,
        oldContent: String,
        newContent: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project = projectDao.getProjectById(projectId)
                ?: return@withContext Result.failure(ProjectNotFoundException(projectId))

            val file = File(project.rootPath, path)
            if (!file.exists()) {
                return@withContext Result.failure(FileNotFoundException(path))
            }

            val currentContent = file.readText()
            if (!currentContent.contains(oldContent)) {
                return@withContext Result.failure(PatchNotFoundException(path, oldContent))
            }

            val patchedContent = currentContent.replace(oldContent, newContent)
            file.writeText(patchedContent)

            projectDao.updateUpdatedAt(projectId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createTemplateFiles(projectDir: File, template: ProjectTemplate) {
        when (template) {
            ProjectTemplate.BLANK -> createBlankTemplate(projectDir)
            ProjectTemplate.HTML_BASIC -> createBasicHtmlTemplate(projectDir)
            ProjectTemplate.TAILWIND -> createTailwindTemplate(projectDir)
            ProjectTemplate.LANDING_PAGE -> createLandingPageTemplate(projectDir)
        }
    }

    private fun createBlankTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText("")
    }

    private fun createBasicHtmlTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(BASIC_HTML_TEMPLATE)
        File(projectDir, "style.css").writeText(BASIC_CSS_TEMPLATE)
        File(projectDir, "script.js").writeText(BASIC_JS_TEMPLATE)
    }

    private fun createTailwindTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(TAILWIND_HTML_TEMPLATE)
    }

    private fun createLandingPageTemplate(projectDir: File) {
        File(projectDir, "index.html").writeText(LANDING_PAGE_HTML_TEMPLATE)
        File(projectDir, "style.css").writeText(LANDING_PAGE_CSS_TEMPLATE)
    }

    companion object {
        private val BASIC_HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>My Website</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <h1>Hello, World!</h1>
                <p>Welcome to my website.</p>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        private val BASIC_CSS_TEMPLATE = """
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }

            body {
                font-family: system-ui, -apple-system, sans-serif;
                line-height: 1.6;
                padding: 2rem;
            }

            h1 {
                color: #333;
                margin-bottom: 1rem;
            }

            p {
                color: #666;
            }
        """.trimIndent()

        private val BASIC_JS_TEMPLATE = """
            // Your JavaScript code here
            console.log('Hello from JavaScript!');
        """.trimIndent()

        private val TAILWIND_HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>My Tailwind Website</title>
                <script src="https://cdn.tailwindcss.com"></script>
            </head>
            <body class="bg-gray-100 min-h-screen">
                <div class="container mx-auto px-4 py-8">
                    <h1 class="text-4xl font-bold text-gray-800 mb-4">Hello, World!</h1>
                    <p class="text-gray-600">Welcome to my Tailwind CSS website.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        private val LANDING_PAGE_HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Landing Page</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <link rel="stylesheet" href="style.css">
            </head>
            <body class="bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 min-h-screen">
                <nav class="container mx-auto px-6 py-4">
                    <div class="flex items-center justify-between">
                        <div class="text-white text-2xl font-bold">Brand</div>
                        <div class="hidden md:flex space-x-6">
                            <a href="#" class="text-white/90 hover:text-white transition">Home</a>
                            <a href="#" class="text-white/90 hover:text-white transition">Features</a>
                            <a href="#" class="text-white/90 hover:text-white transition">About</a>
                            <a href="#" class="text-white/90 hover:text-white transition">Contact</a>
                        </div>
                    </div>
                </nav>

                <main class="container mx-auto px-6 py-16 text-center">
                    <h1 class="text-5xl md:text-6xl font-bold text-white mb-6">
                        Build Something Amazing
                    </h1>
                    <p class="text-xl text-white/80 mb-8 max-w-2xl mx-auto">
                        Create beautiful, responsive websites with modern tools and frameworks.
                    </p>
                    <div class="flex flex-col sm:flex-row gap-4 justify-center">
                        <button class="px-8 py-3 bg-white text-indigo-600 font-semibold rounded-full hover:bg-opacity-90 transition">
                            Get Started
                        </button>
                        <button class="px-8 py-3 border-2 border-white text-white font-semibold rounded-full hover:bg-white/10 transition">
                            Learn More
                        </button>
                    </div>
                </main>
            </body>
            </html>
        """.trimIndent()

        private val LANDING_PAGE_CSS_TEMPLATE = """
            /* Custom styles */
            body {
                font-family: system-ui, -apple-system, sans-serif;
            }
        """.trimIndent()
    }
}

data class SearchReplaceResult(
    var filesModified: Int = 0,
    var totalReplacements: Int = 0,
    val files: MutableList<SearchReplaceFileResult> = mutableListOf()
)

data class SearchReplaceFileResult(
    val path: String,
    val replacements: Int
)

class ProjectExistsException(name: String) : Exception("Project '$name' already exists")
class ProjectNotFoundException(id: String) : Exception("Project not found: $id")
class ProjectCreationException(message: String) : Exception(message)
class FileNotFoundException(path: String) : Exception("File not found: $path")
class FileExistsException(path: String) : Exception("File already exists: $path")
class FileDeletionException(path: String) : Exception("Failed to delete: $path")
class FileRenameException(oldPath: String, newPath: String) : Exception("Failed to rename $oldPath to $newPath")
class FolderCreationException(path: String) : Exception("Failed to create folder: $path")
class PatchNotFoundException(path: String, content: String) : Exception("Content not found in $path: ${content.take(50)}...")
