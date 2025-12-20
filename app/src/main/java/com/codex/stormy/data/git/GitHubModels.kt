package com.codex.stormy.data.git

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub API response models for Actions/Workflows
 */

@Serializable
data class GitHubWorkflowsResponse(
    @SerialName("total_count") val totalCount: Int,
    val workflows: List<GitHubWorkflow>
)

@Serializable
data class GitHubWorkflow(
    val id: Long,
    @SerialName("node_id") val nodeId: String,
    val name: String,
    val path: String,
    val state: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("badge_url") val badgeUrl: String? = null
)

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun>
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("node_id") val nodeId: String,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String,
    @SerialName("run_number") val runNumber: Int,
    val event: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("workflow_id") val workflowId: Long,
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("jobs_url") val jobsUrl: String,
    @SerialName("logs_url") val logsUrl: String,
    @SerialName("check_suite_url") val checkSuiteUrl: String? = null,
    @SerialName("artifacts_url") val artifactsUrl: String,
    val actor: GitHubUser? = null,
    @SerialName("triggering_actor") val triggeringActor: GitHubUser? = null,
    @SerialName("head_commit") val headCommit: GitHubCommitInfo? = null
)

@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    @SerialName("node_id") val nodeId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    val type: String? = null
)

@Serializable
data class GitHubCommitInfo(
    val id: String,
    @SerialName("tree_id") val treeId: String? = null,
    val message: String,
    val timestamp: String? = null,
    val author: GitHubAuthor? = null,
    val committer: GitHubAuthor? = null
)

@Serializable
data class GitHubAuthor(
    val name: String,
    val email: String
)

@Serializable
data class GitHubJobsResponse(
    @SerialName("total_count") val totalCount: Int,
    val jobs: List<GitHubJob>
)

@Serializable
data class GitHubJob(
    val id: Long,
    @SerialName("run_id") val runId: Long,
    @SerialName("run_url") val runUrl: String,
    @SerialName("node_id") val nodeId: String,
    @SerialName("head_sha") val headSha: String,
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val name: String,
    val steps: List<GitHubJobStep>? = null,
    @SerialName("runner_id") val runnerId: Long? = null,
    @SerialName("runner_name") val runnerName: String? = null
)

@Serializable
data class GitHubJobStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

/**
 * Domain models for the app
 */

enum class CIStatus {
    SUCCESS,
    FAILURE,
    CANCELLED,
    PENDING,
    IN_PROGRESS,
    SKIPPED,
    UNKNOWN
}

data class WorkflowInfo(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    val badgeUrl: String?,
    val htmlUrl: String
)

data class WorkflowRunInfo(
    val id: Long,
    val name: String,
    val branch: String?,
    val commitSha: String,
    val commitMessage: String?,
    val runNumber: Int,
    val event: String,
    val status: CIStatus,
    val conclusion: CIStatus?,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String?,
    val actorName: String?,
    val actorAvatarUrl: String?
) {
    val displayStatus: CIStatus
        get() = conclusion ?: status
}

data class JobInfo(
    val id: Long,
    val name: String,
    val status: CIStatus,
    val conclusion: CIStatus?,
    val startedAt: String?,
    val completedAt: String?,
    val steps: List<JobStepInfo>,
    val htmlUrl: String
) {
    val displayStatus: CIStatus
        get() = conclusion ?: status
}

data class JobStepInfo(
    val name: String,
    val number: Int,
    val status: CIStatus,
    val conclusion: CIStatus?
) {
    val displayStatus: CIStatus
        get() = conclusion ?: status
}

/**
 * Helper functions to convert API models to domain models
 */

fun GitHubWorkflowRun.toWorkflowRunInfo(): WorkflowRunInfo {
    return WorkflowRunInfo(
        id = id,
        name = name ?: "Workflow Run #$runNumber",
        branch = headBranch,
        commitSha = headSha,
        commitMessage = headCommit?.message,
        runNumber = runNumber,
        event = event,
        status = status.toCIStatus(),
        conclusion = conclusion?.toCIStatus(),
        htmlUrl = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        startedAt = runStartedAt,
        actorName = actor?.login,
        actorAvatarUrl = actor?.avatarUrl
    )
}

fun GitHubWorkflow.toWorkflowInfo(): WorkflowInfo {
    return WorkflowInfo(
        id = id,
        name = name,
        path = path,
        state = state,
        badgeUrl = badgeUrl,
        htmlUrl = htmlUrl
    )
}

fun GitHubJob.toJobInfo(): JobInfo {
    return JobInfo(
        id = id,
        name = name,
        status = status.toCIStatus(),
        conclusion = conclusion?.toCIStatus(),
        startedAt = startedAt,
        completedAt = completedAt,
        steps = steps?.map { it.toJobStepInfo() } ?: emptyList(),
        htmlUrl = htmlUrl
    )
}

fun GitHubJobStep.toJobStepInfo(): JobStepInfo {
    return JobStepInfo(
        name = name,
        number = number,
        status = status.toCIStatus(),
        conclusion = conclusion?.toCIStatus()
    )
}

fun String.toCIStatus(): CIStatus {
    return when (this.lowercase()) {
        "success" -> CIStatus.SUCCESS
        "failure", "failed" -> CIStatus.FAILURE
        "cancelled", "canceled" -> CIStatus.CANCELLED
        "pending", "queued", "waiting" -> CIStatus.PENDING
        "in_progress" -> CIStatus.IN_PROGRESS
        "skipped" -> CIStatus.SKIPPED
        "completed" -> CIStatus.SUCCESS // Default completed to success, actual status from conclusion
        else -> CIStatus.UNKNOWN
    }
}
