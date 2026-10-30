package com.oro.app.ui.screens.shield

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.guard.GuardAgent
import com.oro.app.guard.GuardDailyPass
import com.oro.app.guard.GuardSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * The shield screen's state: the guard conversation, the council roster, the
 * findings board, and the last project the guard may scan. All the work
 * happens in [GuardAgent]; this is its window.
 */
class ShieldViewModel(app: Application) : AndroidViewModel(app) {

    private val app = getApplication<Application>()
    private val aiRepository: com.oro.app.data.repository.AiRepository =
        OroApplication.getInstance().aiRepository

    val guardState: StateFlow<GuardAgent.State> = GuardAgent.state

    val findings: StateFlow<List<Map<String, String>>> = flow {
        while (true) {
            emit(GuardSettingsStore.findings(getApplication()))
            kotlinx.coroutines.delay(3_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _models = MutableStateFlow<List<AiModel>>(emptyList())
    val models: StateFlow<List<AiModel>> = _models.asStateFlow()

    private val _memberIds = MutableStateFlow<List<String>>(emptyList())
    val memberIds: StateFlow<List<String>> = _memberIds.asStateFlow()

    private val _chairId = MutableStateFlow<String?>(null)
    val chair: StateFlow<AiModel?> = combine(_chairId, _memberIds, _models) { id, seats, models ->
        models.firstOrNull { it.id == id }
            ?: seats.firstNotNullOfOrNull { seat -> models.firstOrNull { it.id == seat } }
            ?: models.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dailyPass = MutableStateFlow(false)
    val dailyPass = _dailyPass.asStateFlow()

    private val _runnerUrl = MutableStateFlow("")
    val runnerUrl = _runnerUrl.asStateFlow()

    private val _runnerToken = MutableStateFlow("")
    val runnerToken = _runnerToken.asStateFlow()

    private var projectRoot: File? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { GuardDailyPass.runIfDue(app, aiRepository) }
        }
        viewModelScope.launch {
            _models.value = aiRepository.getAvailableModels()
            _memberIds.value = GuardSettingsStore.memberIds(app)
            _chairId.value = GuardSettingsStore.chairId(app)
            _dailyPass.value = GuardSettingsStore.dailyPassOn(app)
            _runnerUrl.value = GuardSettingsStore.sandboxRunnerUrl(app).orEmpty()
            _runnerToken.value = GuardSettingsStore.sandboxRunnerToken(app).orEmpty()
            OroApplication.getInstance().projectRepository.getAllProjects().collect { projects ->
                projectRoot = projects.maxByOrNull { it.lastOpenedAt ?: 0L }?.rootPath?.let { File(it) }
            }
        }
    }

    /** Sends a message: a new run, or a follow-up into the live one. */
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (GuardAgent.state.value.running) {
            GuardAgent.queueFollowUp(t)
            return
        }
        viewModelScope.launch {
            _models.value = aiRepository.getAvailableModels()
            GuardAgent.start(app, t, aiRepository, model = chair.value, projectRoot = projectRoot)
        }
    }

    fun runAction(action: ShieldAction, input: String = "") = send(action.task(input))

    fun stop() = GuardAgent.stop()
    fun answer(text: String) = GuardAgent.answer(text)
    fun newChat() = GuardAgent.clear()

    fun toggleMember(id: String) {
        viewModelScope.launch {
            val current = GuardSettingsStore.memberIds(app)
            val next = if (id in current) current - id else current + id
            if (next.isEmpty()) return@launch // a council of nobody answers nobody
            GuardSettingsStore.setMemberIds(app, next)
            _memberIds.value = next
            if (_chairId.value == id && id !in next) {
                _chairId.value = next.first()
                GuardSettingsStore.setChairId(app, next.first())
            }
        }
    }

    fun setChair(id: String) {
        viewModelScope.launch {
            GuardSettingsStore.setChairId(app, id)
            _chairId.value = id
        }
    }

    fun setDailyPass(on: Boolean) {
        viewModelScope.launch {
            GuardSettingsStore.setDailyPassOn(app, on)
            _dailyPass.value = on
        }
    }

    fun saveRunner(url: String, token: String) {
        viewModelScope.launch {
            GuardSettingsStore.setSandboxRunnerUrl(app, url)
            GuardSettingsStore.setSandboxRunnerToken(app, token)
            _runnerUrl.value = url.trim()
            _runnerToken.value = token.trim()
        }
    }

    fun clearFindings() {
        viewModelScope.launch { GuardSettingsStore.clearFindings(app) }
    }
}

/** The standing orders the shield offers before the user types. */
enum class ShieldAction(val label: String) {
    INCIDENT("Incident mode") {
        override fun task(input: String) =
            "I think I'm being hacked or breached right now${if (input.isNotBlank()) ". What I'm seeing: $input" else ""}. " +
                "Run incident response: help me contain first (what to stop touching now), preserve evidence, trace what the evidence supports, " +
                "then walk recovery step by step. Use the council on the evidence. Never ask me for a plaintext password."
    },
    TRACE("Trace an attack") {
        override fun task(input: String) =
            "Trace this attack for me. Here is what I have:\n${input.ifBlank { "(I will paste the message, headers or URL next.)" }}\n" +
                "Build an evidence record and timeline, identify the infrastructure from public records only, and draft the abuse reports I should send. " +
                "Never contact the attacker or touch their systems."
    },
    CHECK_URL("Judge a link") {
        override fun task(input: String) =
            "Judge this link before I touch it: $input. Run the structural check, the certificate, and passive enrichment if it smells. " +
                "End with a clear verdict: safe, suspicious or malicious, and what to do."
    },
    PHONE_AUDIT("Audit this phone") {
        override fun task(input: String) =
            "Audit this phone's security posture end to end. Then explain the findings in plain words, worst first, with the exact setting to change for each."
    },
    VAULT_AUDIT("Audit saved logins") {
        override fun task(input: String) =
            "Audit the logins in Oro's vault for breach exposure and reuse. Tell me exactly which entries to rotate first and why; never quote a password."
    },
    PROJECT_SECRETS("Scan for secrets") {
        override fun task(input: String) =
            "Scan the open project for escaped credentials. For every finding: what it is, where, whether it must be treated as leaked, and the rotation order. Values stay masked."
    },
    HARDEN("Harden my project") {
        override fun task(input: String) =
            "Harden the open project. Audit dependencies, scan for secrets, and if I give you a URL, review the deployed site's headers and mail authentication. " +
                "Then patch the project files yourself and leave me a short report of what changed. URL: ${input.ifBlank { "(none yet)" }}"
    },
    KEV_NEWS("Exploitation news") {
        override fun task(input: String) =
            "Read CISA's Known-Exploited-Vulnerabilities catalog for the last 30 days. Summarise what is being exploited in the wild right now, ransomware-linked first, " +
                "and say which of it could plausibly touch me (phone apps, home routers, common dev dependencies)."
    },
    ACCOUNTS("Check my accounts") {
        override fun task(input: String) =
            "Walk my own account security pages in the browser with me: active sessions, 2FA and recovery options, starting with email. " +
                "Ask before any sensitive action and hand me any captcha."
    },
    COUNCIL("Ask the council") {
        override fun task(input: String) =
            "Convene the security council on this and bring me the merged record: $input"
    },
    FREEFORM("Ask the Shield") {
        override fun task(input: String) = input
    };

    abstract fun task(input: String): String
}
