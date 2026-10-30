package com.oro.app.ui.screens.shield

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.repository.ProjectRepository
import com.oro.app.guard.GuardAgent
import com.oro.app.guard.GuardSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * The shield screen's state: the guard's run, the council roster, the
 * findings board, and the last project the guard may scan. All the work
 * happens in [GuardAgent]; this is its window.
 */
class ShieldViewModel(app: Application) : AndroidViewModel(app) {

    private val app = getApplication<Application>()
    private val aiRepository: com.oro.app.data.repository.AiRepository =
        OroApplication.getInstance().let { it.aiRepository }

    val guardState: StateFlow<GuardAgent.State> = GuardAgent.state

    val findings: StateFlow<List<Map<String, String>>> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(GuardSettingsStore.findings(getApplication()))
            kotlinx.coroutines.delay(4_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _memberIds = MutableStateFlow<List<String>>(emptyList())
    val memberIds = _memberIds.asStateFlow()

    private val _dailyPass = MutableStateFlow(false)
    val dailyPass = _dailyPass.asStateFlow()

    val models: StateFlow<List<AiModel>> = MutableStateFlow(aiRepository.models)

    val seatedNames: StateFlow<List<String>> = _memberIds.map { ids ->
        ids.mapNotNull { id -> aiRepository.models.firstOrNull { it.id == id }?.name ?: id.substringAfter(':') }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var projectRoot: File? = null

    init {
        viewModelScope.launch {
            (models as MutableStateFlow).value = aiRepository.models
            _memberIds.value = GuardSettingsStore.memberIds(app)
            _dailyPass.value = GuardSettingsStore.dailyPassOn(app)
            ProjectRepository(app).getAllProjects().collect { projects ->
                projectRoot = projects.maxByOrNull { it.lastOpenedAt ?: 0L }?.rootPath?.let { File(it) }
            }
        }
    }

    fun run(task: String) {
        viewModelScope.launch {
            GuardAgent.start(app, task, aiRepository, projectRoot = projectRoot)
        }
    }

    fun runAction(action: ShieldAction, input: String = "") {
        run(action.task(input))
    }

    fun stop() = GuardAgent.stop()
    fun answer(text: String) = GuardAgent.answer(text)
    fun clear() = GuardAgent.clear()

    fun toggleMember(id: String) {
        viewModelScope.launch {
            val current = GuardSettingsStore.memberIds(app)
            val next = if (id in current) current - id else current + id
            if (next.isEmpty()) return@launch // a council of nobody answers nobody
            GuardSettingsStore.setMemberIds(app, next)
            _memberIds.value = next
        }
    }

    fun setDailyPass(on: Boolean) {
        viewModelScope.launch {
            GuardSettingsStore.setDailyPassOn(app, on)
            _dailyPass.value = on
        }
    }

    fun clearFindings() {
        viewModelScope.launch { GuardSettingsStore.clearFindings(app) }
    }
}

/** The standing orders the action cards give the guard. */
enum class ShieldAction(val label: String) {
    PHONE_AUDIT("Audit this phone") {
        override fun task(input: String) =
            "Audit this phone's security posture end to end. Then explain the findings to the user in plain words, worst first, with the exact setting to change for each."
    },
    VAULT_AUDIT("Audit saved logins") {
        override fun task(input: String) =
            "Audit the logins in Oro's vault for breach exposure and reuse. Tell the user exactly which entries to rotate first and why; never quote a password."
    },
    PROJECT_SECRETS("Scan project for secrets") {
        override fun task(input: String) =
            "Scan the open project for escaped credentials. For every finding: what it is, where, whether it must be treated as leaked, and the rotation order. Values stay masked."
    },
    KEV_NEWS("Exploitation news") {
        override fun task(input: String) =
            "Read CISA's Known-Exploited-Vulnerabilities catalog for the last 30 days. Summarise what is being exploited in the wild right now, ransomware-linked first, and say which of it could plausibly touch this user (phone apps, home routers, common dev dependencies)."
    },
    CHECK_URL("Judge a link") {
        override fun task(input: String) =
            "Judge this link before I touch it: $input. Run the structural check, the certificate, and if anything smells, convene the council on it. End with a clear verdict: safe, suspicious, or malicious -- and what to do."
    },
    INCIDENT("Incident mode") {
        override fun task(input: String) =
            "The user believes they are being hacked or breached right now${if (input.isNotBlank()) ": $input" else ""}. Run incident response: calm them, contain first (what to stop touching NOW), then verify what actually happened with the tools, convene the council on the evidence, then walk recovery step by step -- passwords from a clean device, sessions, 2FA, breach notifications. Ask the user what they are seeing when you need it. Never ask for a plaintext password in chat."
    },
    COUNCIL("Ask the council") {
        override fun task(input: String) =
            "Convene the security council on this and bring me the merged record: $input"
    },
    FREEFORM("Ask the guard") {
        override fun task(input: String) = input
    };

    abstract fun task(input: String): String
}
