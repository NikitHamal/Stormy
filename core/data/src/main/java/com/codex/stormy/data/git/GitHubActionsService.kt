package com.codex.stormy.data.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with GitHub Actions API
 * Provides access to workflows, runs, and jobs
 */
class GitHubActionsService(
    private val credentialsManager: GitCredentialsManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
    }

    /**
     * Parse GitHub remote URL to extract owner and repo
     * Supports both HTTPS and SSH formats:
     * - https://github.com/owner/repo.git
     * - git@github.com:owner/repo.git
     */
    fun parseGitHubUrl(remoteUrl: String?): Pair<String, String>? {
        if (remoteUrl == null) return null

        // HTTPS format
        val httpsRegex = Regex("""https?://github\.com/([^/]+)/([^/]+?)(?:\.git)?/?$""")
        httpsRegex.find(remoteUrl)?.let { match ->
            return match.groupValues[1] to match.groupValues[2]
        }

        // SSH format
        val sshRegex = Regex("""git@github\.com:([^/]+)/([^/]+?)(?:\.git)?/?$""")
        sshRegex.find(remoteUrl)?.let { match ->
            return match.groupValues[1] to match.groupValues[2]
        }

        return null
    }

    /**
     * Get all workflows for a repository
     */
    suspend fun getWorkflows(
        owner: String,
        repo: String
    ): GitOperationResult<List<WorkflowInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$GITHUB_API_BASE/repos/$owner/$repo/actions/workflows")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to fetch workflows: ${response.code} ${response.message}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext GitOperationResult.Error("Empty response")

                val workflowsResponse = json.decodeFromString<GitHubWorkflowsResponse>(body)
                val workflows = workflowsResponse.workflows.map { it.toWorkflowInfo() }

                GitOperationResult.Success(workflows)
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to fetch workflows: ${e.message}", e)
        }
    }

    /**
     * Get workflow runs for a repository
     */
    suspend fun getWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: Long? = null,
        branch: String? = null,
        status: String? = null,
        perPage: Int = 10
    ): GitOperationResult<List<WorkflowRunInfo>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = if (workflowId != null) {
                "$GITHUB_API_BASE/repos/$owner/$repo/actions/workflows/$workflowId/runs"
            } else {
                "$GITHUB_API_BASE/repos/$owner/$repo/actions/runs"
            }

            val params = mutableListOf<String>()
            branch?.let { params.add("branch=$it") }
            status?.let { params.add("status=$it") }
            params.add("per_page=$perPage")

            val url = if (params.isNotEmpty()) {
                "$baseUrl?${params.joinToString("&")}"
            } else {
                baseUrl
            }

            val request = buildRequest(url)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to fetch workflow runs: ${response.code} ${response.message}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext GitOperationResult.Error("Empty response")

                val runsResponse = json.decodeFromString<GitHubWorkflowRunsResponse>(body)
                val runs = runsResponse.workflowRuns.map { it.toWorkflowRunInfo() }

                GitOperationResult.Success(runs)
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to fetch workflow runs: ${e.message}", e)
        }
    }

    /**
     * Get the latest workflow run for a branch
     */
    suspend fun getLatestWorkflowRun(
        owner: String,
        repo: String,
        branch: String
    ): GitOperationResult<WorkflowRunInfo?> = withContext(Dispatchers.IO) {
        val result = getWorkflowRuns(owner, repo, branch = branch, perPage = 1)
        when (result) {
            is GitOperationResult.Success -> {
                GitOperationResult.Success(result.data.firstOrNull())
            }
            is GitOperationResult.Error -> result
            is GitOperationResult.InProgress -> result
        }
    }

    /**
     * Get jobs for a workflow run
     */
    suspend fun getWorkflowRunJobs(
        owner: String,
        repo: String,
        runId: Long
    ): GitOperationResult<List<JobInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(
                "$GITHUB_API_BASE/repos/$owner/$repo/actions/runs/$runId/jobs"
            )

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to fetch jobs: ${response.code} ${response.message}"
                    )
                }

                val body = response.body?.string()
                    ?: return@withContext GitOperationResult.Error("Empty response")

                val jobsResponse = json.decodeFromString<GitHubJobsResponse>(body)
                val jobs = jobsResponse.jobs.map { it.toJobInfo() }

                GitOperationResult.Success(jobs)
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to fetch jobs: ${e.message}", e)
        }
    }

    /**
     * Re-run a workflow
     */
    suspend fun rerunWorkflow(
        owner: String,
        repo: String,
        runId: Long
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/runs/$runId/rerun")
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .apply {
                    getAuthToken()?.let { token ->
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to re-run workflow: ${response.code} ${response.message}"
                    )
                }

                GitOperationResult.Success(Unit, "Workflow re-run triggered")
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to re-run workflow: ${e.message}", e)
        }
    }

    /**
     * Cancel a workflow run
     */
    suspend fun cancelWorkflowRun(
        owner: String,
        repo: String,
        runId: Long
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/runs/$runId/cancel")
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .apply {
                    getAuthToken()?.let { token ->
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to cancel workflow: ${response.code} ${response.message}"
                    )
                }

                GitOperationResult.Success(Unit, "Workflow run cancelled")
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to cancel workflow: ${e.message}", e)
        }
    }

    /**
     * Trigger a workflow dispatch event
     */
    suspend fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): GitOperationResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildString {
                append("{\"ref\":\"$ref\"")
                if (inputs.isNotEmpty()) {
                    append(",\"inputs\":{")
                    append(inputs.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" })
                    append("}")
                }
                append("}")
            }

            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$owner/$repo/actions/workflows/$workflowId/dispatches")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .apply {
                    getAuthToken()?.let { token ->
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GitOperationResult.Error(
                        "Failed to trigger workflow: ${response.code} ${response.message}"
                    )
                }

                GitOperationResult.Success(Unit, "Workflow triggered")
            }
        } catch (e: Exception) {
            GitOperationResult.Error("Failed to trigger workflow: ${e.message}", e)
        }
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .apply {
                getAuthToken()?.let { token ->
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
    }

    private fun getAuthToken(): String? {
        // Try to get GitHub credentials
        return try {
            val credentials = kotlinx.coroutines.runBlocking {
                credentialsManager.getCredentialsForUrl("https://github.com")
            }
            // For GitHub, the password field typically contains the personal access token
            credentials?.password
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * CI/CD status summary for a repository
 */
data class CIStatusSummary(
    val isGitHubRepo: Boolean,
    val owner: String?,
    val repo: String?,
    val latestRun: WorkflowRunInfo?,
    val recentRuns: List<WorkflowRunInfo>,
    val workflows: List<WorkflowInfo>,
    val currentBranchStatus: CIStatus?,
    val errorMessage: String?
) {
    companion object {
        val NOT_GITHUB = CIStatusSummary(
            isGitHubRepo = false,
            owner = null,
            repo = null,
            latestRun = null,
            recentRuns = emptyList(),
            workflows = emptyList(),
            currentBranchStatus = null,
            errorMessage = "Not a GitHub repository"
        )

        fun error(message: String) = CIStatusSummary(
            isGitHubRepo = false,
            owner = null,
            repo = null,
            latestRun = null,
            recentRuns = emptyList(),
            workflows = emptyList(),
            currentBranchStatus = null,
            errorMessage = message
        )
    }
}
