package com.hopa.app.feature.chat

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopa.app.di.AppContainer
import com.hopa.app.di.Surfaces
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelRequest
import com.hopa.engine.loop.TurnContext
import com.hopa.engine.observation.ScreenPath
import com.hopa.engine.observation.ScreenSource
import com.hopa.engine.observation.ScreenSourceDevice
import com.hopa.stores.AppSettings
import com.hopa.stores.Attachment
import com.hopa.stores.AttachmentRules
import com.hopa.stores.ChatMessage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ChatViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            container.approval.pending.collect { request ->
                _state.update { it.copy(pendingApproval = request) }
            }
        }
        viewModelScope.launch {
            container.pendingAttachment.collect { attachment ->
                if (attachment?.path != _state.value.attachment?.path) {
                    _state.update { it.copy(attachment = attachment) }
                }
            }
        }
        viewModelScope.launch {
            container.pendingNotice.collect { notice ->
                _state.update { it.copy(attachmentNotice = notice) }
            }
        }
        viewModelScope.launch {
            container.selectedConversation.collect { id ->
                if (id == null || id == _state.value.current?.id) {
                    return@collect
                }
                val all = container.conversations.all()
                val selected = all.firstOrNull { it.id == id } ?: return@collect
                _state.update {
                    it.copy(current = selected, conversations = all, options = emptyList())
                }
            }
        }
    }

    fun onInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    /** A deep link or a shared selection lands in the composer, never sent. */
    fun prefill(text: String) {
        _state.update { it.copy(input = text.trim()) }
    }

    // ── Voice ─────────────────────────────────────────────────────────────────

    fun startVoice() {
        if (_state.value.busy || _state.value.recording) {
            return
        }
        if (!container.voice.hasPermission()) {
            _state.update { it.copy(voiceError = "Microphone permission is off") }
            return
        }
        if (!container.voice.start()) {
            _state.update { it.copy(voiceError = "Could not start the microphone") }
            return
        }
        _state.update { it.copy(recording = true, voiceError = null) }
    }

    fun stopVoice() {
        if (!_state.value.recording) {
            return
        }
        _state.update { it.copy(recording = false, transcribing = true) }
        viewModelScope.launch {
            val wav = runCatching { container.voice.stop() }.getOrNull()
            if (wav == null) {
                _state.update { it.copy(transcribing = false, voiceError = "Nothing was recorded") }
                return@launch
            }
            val result = runCatching { container.transcribe(wav) }
            val text = result.getOrNull()
            _state.update {
                it.copy(
                    transcribing = false,
                    input = text?.takeIf { value -> value.isNotBlank() } ?: it.input,
                    voiceError = when {
                        result.isFailure -> "Voice failed: ${result.exceptionOrNull()?.message}"
                        text == null -> "Connect the bridge to dictate"
                        text.isBlank() -> "I did not catch that"
                        else -> null
                    },
                )
            }
        }
    }

    fun dismissVoiceError() {
        _state.update { it.copy(voiceError = null) }
    }

    /** Read a reply through the PC voice or Android TTS. */
    fun speak(text: String) {
        if (text.isBlank()) {
            return
        }
        viewModelScope.launch { container.speak(text) }
    }

    // ── Conversation management ───────────────────────────────────────────────

    fun newChat() {
        viewModelScope.launch {
            val created = container.conversations.create()
            container.selectedConversation.value = created.id
            container.settings.update { it.copy(selectedConversation = created.id) }
            _state.update {
                it.copy(
                    current = created,
                    conversations = container.conversations.all(),
                    input = "",
                    error = null,
                    options = emptyList(),
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            container.conversations.delete(id)
            load()
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch {
            container.conversations.rename(id, title)
            load()
        }
    }

    /** Remove the last exchange from the thread. */
    fun undoLast() {
        if (_state.value.busy) {
            return
        }
        val conversation = _state.value.current ?: return
        if (conversation.messages.size < 2) {
            return
        }
        viewModelScope.launch {
            container.conversations.dropLastExchange(conversation.id)
            val all = container.conversations.all()
            _state.update {
                it.copy(
                    current = all.firstOrNull { item -> item.id == conversation.id },
                    conversations = all,
                )
            }
        }
    }

    /** Re-run the last user goal without appending the message twice. */
    fun retryLast() {
        if (_state.value.busy) {
            return
        }
        val conversation = _state.value.current ?: return
        val lastUser = conversation.messages.lastOrNull { it.role == "user" } ?: return
        sendText(lastUser.text, appendUser = false)
    }

    // ── Send dispatch ─────────────────────────────────────────────────────────

    fun send() {
        val text = _state.value.input.trim()
        val attachment = _state.value.attachment
        if (text.isEmpty() && attachment == null) {
            return
        }
        if (_state.value.busy) {
            // The main turn is still going. A second goal goes to its own hidden
            // display instead of being dropped on the floor.
            if (text.isNotBlank() && container.runner.canStart()) {
                container.runner.start(text)
                _state.update { it.copy(input = "") }
            }
            return
        }
        sendText(text, attachment = attachment)
    }

    /** Drop the shared file before it is sent, and remove its bytes. */
    fun discardAttachment() {
        val attachment = _state.value.attachment
        container.pendingAttachment.value = null
        container.pendingNotice.value = null
        _state.update { it.copy(attachment = null, attachmentNotice = null) }
        if (attachment != null) {
            viewModelScope.launch { container.intake.delete(attachment) }
        }
    }

    fun dismissAttachmentNotice() {
        container.pendingNotice.value = null
        _state.update { it.copy(attachmentNotice = null) }
    }

    fun sendOption(option: String) {
        sendText(option)
    }

    fun sendQuick(text: String) {
        sendText(text)
    }

    private var planApprovedFor = ""
    private var pendingGoal = ""

    /** The person read the plan; run exactly that goal now. */
    fun approvePlan() {
        val goal = pendingGoal
        val plan = _state.value.pendingPlan
        if (goal.isBlank() || plan == null) {
            return
        }
        planApprovedFor = goal
        _state.update { it.copy(pendingPlan = null) }
        // The message is already in the chat: the plan was read above it.
        sendText(goal, appendUser = false)
    }

    fun cancelPlan() {
        pendingGoal = ""
        _state.update { it.copy(pendingPlan = null) }
    }

    /** Keep the work as a reusable skill, so the next similar ask starts ahead. */
    fun saveSkill(goal: String, steps: List<String>) {
        if (goal.isBlank() || steps.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val saved = runCatching {
                container.skills.add(
                    trigger = goal.take(80),
                    action = steps.joinToString("\n"),
                )
            }.isSuccess
            if (saved) {
                _state.update { it.copy(savedGoals = it.savedGoals + goal) }
            } else {
                _state.update { it.copy(error = "Could not save the skill.") }
            }
        }
    }

    fun resolveApproval(allow: Boolean) {
        container.approval.resolve(allow)
    }

    // ── Core send loop ────────────────────────────────────────────────────────

    private fun sendText(
        text: String,
        attachment: Attachment? = null,
        appendUser: Boolean = true,
    ) {
        val conversation = _state.value.current ?: return
        val body = text.ifBlank { attachment?.let(AttachmentRules::describe).orEmpty() }
        if (planApprovedFor.isNotEmpty() && planApprovedFor != body) {
            planApprovedFor = ""
        }
        _state.update {
            it.copy(
                input = "",
                busy = true,
                error = null,
                options = emptyList(),
                attachment = null,
                attachmentNotice = null,
            )
        }
        container.pendingAttachment.value = null
        container.pendingNotice.value = null
        // A task delivered to the other device may hold the phone. Two loops on
        // one screen would fight over it and cancel each other.
        if (!container.busy.compareAndSet(expect = false, update = true)) {
            _state.update {
                it.copy(error = "HOPA is busy with a task from your other device", busy = false)
            }
            return
        }
        container.cancelRequested.value = false
        startRunService()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (appendUser) appendUserMessage(conversation.id, body, attachment, now)
            val history = conversation.messages.map { ModelMessage(it.role, it.text) }
            val current = container.settings.settings.first()
            container.useGate(current.trustedApps.toList())

            // Plan-first gate: show the plan before anything is tapped.
            if (current.planFirst && planApprovedFor != body) {
                val steps = fetchPlanSteps(body)
                container.busy.value = false
                stopRunService()
                if (steps.isNullOrEmpty()) {
                    _state.update { it.copy(busy = false, error = "No plan came back") }
                } else {
                    pendingGoal = body
                    _state.update { it.copy(busy = false, pendingPlan = steps) }
                }
                return@launch
            }

            val memory = buildMemoryContext(current)
            val skills = runCatching { container.skills.promptText() }.getOrDefault("")
            val installed = runCatching { container.packages.installed() }.getOrDefault(emptyList())

            val preferred = ScreenPath.of(current.screenPath)
            val device = container.surfaces(current)
            device.observe()
            if (device.sourceName == "fallback") {
                handleNoDevice(device, preferred, conversation.id)
                return@launch
            }

            // A goal that worked before runs from its recipe: no model, no tokens.
            if (current.replaySkills) {
                val replayed = replayIfPossible(body, device, conversation.id, now, preferred)
                if (replayed) {
                    Surfaces.release()
                    return@launch
                }
            }

            val loop = container.loopFor(device)
            val result = try {
                withTimeoutOrNull(120_000) {
                    loop.run(
                        runId = "run-$now",
                        traceId = UUID.randomUUID().toString(),
                        context = TurnContext(
                            goal = text,
                            history = history,
                            memory = memory,
                            skills = skills,
                            installedApps = installed,
                            appFunctions = typedFunctions(device),
                            vision = current.vision,
                            platform = "android",
                            phoneAccess = device.sourceName != "fallback",
                        ),
                        isCancelled = { container.cancelRequested.value },
                    )
                }
            } finally {
                stopRunService()
                (background as? com.hopa.capabilities.display.ShellDisplayDevice)?.stop()
            }
            recordTurn(result, body, device, conversation.id, now, preferred, history)
        }.invokeOnCompletion { container.busy.value = false }
    }

    // ── sendText helpers ──────────────────────────────────────────────────────

    /** Posts a system message explaining why the action cannot proceed. */
    private suspend fun handleNoDevice(
        device: ScreenSourceDevice,
        preferred: ScreenPath,
        conversationId: String,
    ) {
        container.busy.value = false
        stopRunService()
        container.conversations.append(
            conversationId,
            ChatMessage(
                id = "m-${System.currentTimeMillis()}-screen",
                role = "assistant",
                text = "I cannot reach your apps: " +
                    device.lastError.take(180).ifBlank { "no screen answered" } +
                    ". " +
                    when (preferred) {
                        ScreenPath.BRIDGE ->
                            "Open Act and set up My computer, or choose This phone."
                        ScreenPath.BACKGROUND ->
                            "Open Act: start the hidden screen or turn on screen access."
                        else ->
                            "Open Act: turn on screen access, start the hidden " +
                                "screen, or choose My computer."
                    },
                timestamp = System.currentTimeMillis(),
            ),
        )
        val all = container.conversations.all()
        _state.update {
            it.copy(
                current = all.firstOrNull { item -> item.id == conversationId },
                conversations = all,
                busy = false,
            )
        }
    }

    /**
     * Tries to replay the last matching recipe. Returns true only when the whole
     * recipe ran and the screen ended where it ended last time; otherwise the
     * caller falls through to the model with the screen as it is now.
     */
    private suspend fun replayIfPossible(
        body: String,
        device: ScreenSourceDevice,
        conversationId: String,
        now: Long,
        preferred: ScreenPath,
    ): Boolean {
        val recipe = runCatching { container.recipes.find(body) }.getOrNull() ?: return false
        val outcome = runCatching {
            com.hopa.app.recipes.Replayer(container).replay(recipe, device)
        }.getOrNull() ?: return false
        if (!outcome.trustworthy) return false
        val replayed = outcome.done

        // The recipe knows the taps, not the words: one cheap call reads the
        // final screen and answers, so the person gets "Wi-Fi" and not
        // "repeated 2 steps".
        val screen = outcome.screen
        val spoken = if (screen != null) {
            runCatching { com.hopa.app.recipes.ReplayAnswer(container.model).answer(body, screen) }
                .getOrNull()
        } else {
            null
        }
        val answer = spoken?.first ?: "Repeated the $replayed steps that worked last time."
        val tokens = spoken?.second ?: 0

        container.busy.value = false
        val steps = recipe.steps.mapIndexed { index, action -> "${index + 1}. ${action.kind}" }
        container.conversations.append(
            conversationId,
            ChatMessage(
                id = "m-${System.currentTimeMillis()}-replay",
                role = "assistant",
                text = answer,
                timestamp = System.currentTimeMillis(),
                proof = com.hopa.stores.MessageProof(
                    path = device.sourceName,
                    executed = replayed,
                    verified = replayed,
                    tokens = tokens,
                    millis = System.currentTimeMillis() - now,
                    steps = steps,
                    fellBack = preferred == ScreenPath.AUTO &&
                        device.sourceName == "accessibility",
                ),
            ),
        )
        runCatching {
            container.runHistory.record(
                at = System.currentTimeMillis(),
                goal = body,
                path = device.sourceName,
                steps = replayed,
                tokens = tokens,
                conversationId = conversationId,
            )
        }
        val shown = container.conversations.all()
        _state.update {
            it.copy(
                current = shown.firstOrNull { item -> item.id == conversationId },
                conversations = shown,
                busy = false,
            )
        }
        return true
    }

    /**
     * Asks the model for a numbered plan. Returns steps, or null when the model
     * did not respond or produced an empty list.
     */
    private suspend fun fetchPlanSteps(body: String): List<String>? =
        runCatching {
            container.model.complete(
                ModelRequest(
                    messages = listOf(
                        ModelMessage(
                            "system",
                            "You are planning, not acting. Reply with a numbered " +
                                "plan of at most six short steps, one per line, " +
                                "no other text.",
                        ),
                        ModelMessage("user", body),
                    ),
                    maxTokens = 400,
                ),
            ).text
        }.getOrNull()
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(Regex("^\\d+[.)]\\s*"), "") }
            .take(6)
            .toList()
            .takeIf { it.isNotEmpty() }

    /** Merges memory facts and character tone into a single prompt block. */
    private suspend fun buildMemoryContext(settings: AppSettings): String {
        val facts = runCatching { container.memory.promptText() }.getOrDefault("")
        val character = "Character: ${settings.tone} tone, ${settings.chattiness} length."
        return listOf(facts, character).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /** Appends the user message to the DB and immediately reflects it in state. */
    private suspend fun appendUserMessage(
        conversationId: String,
        body: String,
        attachment: Attachment?,
        now: Long,
    ) {
        container.conversations.append(
            conversationId,
            ChatMessage(
                id = "m-$now-${UUID.randomUUID().toString().take(6)}",
                role = "user",
                text = body,
                timestamp = now,
                attachment = attachment,
            ),
        )
        // The store is the source of truth, but the screen reads state, so the
        // bubble must be published now or it waits until the answer arrives.
        val shown = container.conversations.all()
        _state.update {
            it.copy(
                current = shown.firstOrNull { item -> item.id == conversationId },
                conversations = shown,
            )
        }
    }

    /** Persists the loop result and publishes the final state to the UI. */
    private suspend fun recordTurn(
        result: com.hopa.engine.loop.TurnResult?,
        body: String,
        device: ScreenSourceDevice,
        conversationId: String,
        now: Long,
        preferred: ScreenPath,
        history: List<ModelMessage>,
    ) {
        val answer = result?.answer ?: "I stopped after two minutes."
        val steps = result?.records.orEmpty().map { record ->
            val target = record.action.app
                ?: record.action.ref
                ?: record.action.url
                ?: record.action.text?.take(40)
                ?: ""
            "${record.step}. ${record.action.kind}${if (target.isBlank()) "" else " $target"}"
        }
        val proof = com.hopa.stores.MessageProof(
            path = device.sourceName,
            executed = result?.records?.count { it.executed } ?: 0,
            verified = result?.records?.count { it.verified } ?: 0,
            tokens = result?.usage?.totalTokens ?: 0,
            millis = System.currentTimeMillis() - now,
            steps = steps,
            fellBack = preferred == ScreenPath.AUTO && device.sourceName == "accessibility",
        )
        container.conversations.append(
            conversationId,
            ChatMessage(
                id = "m-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                role = "assistant",
                text = answer,
                timestamp = System.currentTimeMillis(),
                proof = proof.takeIf { it.executed > 0 },
            ),
        )
        val all = container.conversations.all()
        container.busy.value = false
        // A recipe is only worth replaying when the turn both finished cleanly and
        // verified something: a launch that ended in "I cannot read the screen"
        // used to be saved, and the next ask then claimed "done" after one step.
        if (
            result != null &&
            result.error == null &&
            result.options.isEmpty() &&
            result.records.any { it.verified }
        ) {
            runCatching {
                container.recipes.save(
                    goal = body,
                    steps = result.records.map { it.action },
                    anchors = result.records.map { it.targetKey.orEmpty() },
                    screenHash = result.records.first().beforeHash.orEmpty(),
                    endHash = result.records.last().afterHash.orEmpty(),
                )
            }
        }
        runCatching {
            container.runHistory.record(
                at = System.currentTimeMillis(),
                goal = body,
                path = device.sourceName,
                steps = steps.size,
                tokens = proof.tokens,
                conversationId = conversationId,
            )
        }
        com.hopa.app.privacy.TurnLog.add(
            com.hopa.app.privacy.TurnLogEntry(
                at = System.currentTimeMillis(),
                path = device.sourceName,
                steps = steps.size,
                tokens = proof.tokens,
                charactersSent = history.sumOf { it.content.length } + body.length,
                goal = body.take(80),
            ),
        )
        _state.update {
            it.copy(
                current = all.firstOrNull { item -> item.id == conversationId },
                conversations = all,
                busy = false,
                error = result?.error?.message,
                options = result?.options.orEmpty(),
            )
        }
        answerOutsideApp(answer)
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    /**
     * A run happens inside another app, so the answer is invisible until the
     * person switches back. Try to bring HOPA forward and leave a notification in
     * case Android refuses a background activity start.
     */
    private suspend fun answerOutsideApp(text: String) {
        if (text.isBlank() || com.hopa.app.service.AppVisibility.foreground) {
            return
        }
        val notify = runCatching { container.settings.settings.first().notifyAnswers }
            .getOrDefault(true)
        if (notify) {
            runCatching { com.hopa.app.service.Notifier.postAnswer(container.context, text) }
        }
        container.bringToFront()
    }

    /**
     * Ask the helper what the foreground app publishes, so the model can choose a
     * typed call over a series of taps. Empty when no shell is available.
     */
    private suspend fun typedFunctions(
        device: com.hopa.engine.observation.ScreenSourceDevice,
    ): String = withContext(Dispatchers.IO) {
        // The helper is reached over a local socket: never from the main thread.
        if (!container.shell.connected) {
            return@withContext ""
        }
        val foreground = runCatching { device.foreground() }.getOrDefault("")
        if (foreground.isBlank()) {
            return@withContext ""
        }
        runCatching { container.shell.appFunctions(foreground).second }.getOrDefault("")
    }

    private fun startRunService() {
        val intent = Intent(container.context, com.hopa.app.service.RunService::class.java)
        runCatching { ContextCompat.startForegroundService(container.context, intent) }
    }

    private fun stopRunService() {
        runCatching {
            container.context.stopService(
                Intent(container.context, com.hopa.app.service.RunService::class.java),
            )
        }
    }

    private suspend fun load() {
        val all = container.conversations.all()
        val savedId = container.settings.settings.first().selectedConversation
        val selectedId = savedId.ifBlank { container.selectedConversation.value }
        val current = all.firstOrNull { it.id == selectedId }
            ?: all.firstOrNull()
            ?: container.conversations.create().also {
                container.selectedConversation.value = it.id
            }
        container.selectedConversation.value = current.id
        container.settings.update { it.copy(selectedConversation = current.id) }
        _state.update {
            it.copy(
                conversations = container.conversations.all(),
                current = current,
                busy = false,
            )
        }
    }
}
