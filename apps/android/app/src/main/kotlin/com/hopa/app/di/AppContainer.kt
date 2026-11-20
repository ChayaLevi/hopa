package com.hopa.app.di

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.hopa.app.BuildConfig
import com.hopa.app.link.session.BackendSession
import com.hopa.app.link.remote.ProcessedTaskStore
import com.hopa.app.link.push.PushTokenFetch
import com.hopa.app.link.remote.RemoteRunRequest
import com.hopa.app.link.remote.RemoteTaskRunner
import com.hopa.app.runtime.RemoteRuns
import com.hopa.feature.usage.UsageDependencies
import com.hopa.feature.memory.MemoryDependencies
import com.hopa.feature.skills.SkillsDependencies
import com.hopa.app.service.AppVisibility
import com.hopa.app.service.Notifier
import com.hopa.app.service.RunService
import com.hopa.observation.source.ScreenSourceDevice
import com.hopa.stores.AppSettings
import com.hopa.capabilities.accessibility.tree.AccessibilityDevice
import com.hopa.inference.client.ModelFactory
import com.hopa.inference.choice.ProviderChoiceRules
import com.hopa.capabilities.system.AndroidClock
import com.hopa.capabilities.system.AndroidPackageCatalog
import com.hopa.capabilities.system.AndroidSystemPort
import com.hopa.contracts.ports.DevicePort
import com.hopa.agent.action.ActionExecutor
import com.hopa.agent.action.ApprovalPolicy
import com.hopa.app.ui.UiApprovalGateway
import com.hopa.data.database.HopaDatabase
import com.hopa.data.store.DbRunRecorder
import com.hopa.runlog.record.RunRecordFactory
import com.hopa.data.store.KeystoreSecretStore
import com.hopa.stores.ConversationStore
import com.hopa.data.store.DbConversationStore
import com.hopa.data.store.DataStoreSettingsRepository
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) : ChatDependencies, ConversationsDependencies,
    ActivityDependencies, MemoryDependencies, SkillsDependencies, UsageDependencies,
    SettingsDependencies, PairingDependencies, SurfaceDependencies {
    override val context: Context = context.applicationContext
    override val settings: SettingsRepository = DataStoreSettingsRepository(
        context = context,
        secrets = KeystoreSecretStore(context),
    )
    init {
        com.hopa.app.privacy.TurnLog.attach(
            com.hopa.app.privacy.TurnLogFile(context),
        )
    }

    private val database = HopaDatabase.build(context)
    override val conversations: ConversationStore = DbConversationStore(database)
    /**
     * The tables this client uses. A stored bundle wins over the bundled
     * snapshot; the file store falls back to it on any problem. `knowledge` is a
     * getter, so a bundle accepted between turns applies on the next one.
     */
    private val bundledKnowledge = com.hopa.data.catalog.AssetCatalogSource(context)
    val knowledgeStore: com.hopa.catalog.store.FileCatalogStore =
        com.hopa.catalog.store.FileCatalogStore(
            directory = java.io.File(context.filesDir, "knowledge"),
            fallback = bundledKnowledge.current(),
        )
    val knowledgeSource: com.hopa.catalog.store.CatalogSource = knowledgeStore
    val knowledge: com.hopa.catalog.store.CatalogSnapshot get() = knowledgeSource.current()
    override val selectedConversation = MutableStateFlow<String?>(null)
    override val runGate = com.hopa.app.runtime.RunGate()
    /** The last wireless-pairing answer, shown on the Act page. */
    val pairingStatus = MutableStateFlow<String?>(null)
    /**
     * Goals handed to the chat from outside (a deep link, a profile home).
     * A queue, not a slot: two links in a row must both run, and the second
     * must reach the parallel runner while the first holds the main turn.
     */
    val pendingGoals = kotlinx.coroutines.channels.Channel<String>(
        kotlinx.coroutines.channels.Channel.UNLIMITED,
    )
    /** A file shared into the app, waiting in the composer. */
    override val pendingAttachment = MutableStateFlow<com.hopa.contracts.chat.Attachment?>(null)
    override val pendingNotice = MutableStateFlow<String?>(null)
    /** Work that must outlive an Activity, such as a one-shot launch pairing. */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    /** The last launch-pairing outcome, shown on the Backend card. */
    override val backendNotice = MutableStateFlow<String?>(null)
    override val intake = com.hopa.app.share.AttachmentIntake(context)
    val packages = AndroidPackageCatalog(context)
    override val recorder = DbRunRecorder(database.runRecordDao())
    override val runHistory = com.hopa.data.store.DbRunHistory(database.runHistoryDao())
    val pendingApprovals = com.hopa.data.store.DbPendingApprovalStore(
        database.pendingApprovalDao(),
    )
    override val memory = com.hopa.data.store.DbMemoryStore(database.memoryDao())
    override val skills = com.hopa.data.store.DbSkillStore(database.skillDao())
    val clock = AndroidClock()
    /**
     * The gate settings, replaced as a whole when they change. The executor
     * reads one snapshot per step, so a turn that starts while Settings is being
     * saved cannot see half of the new list.
     */
    @Volatile
    private var gatePolicy = ApprovalPolicy()

    /** Use these settings for the turns that start now. */
    override fun useGate(trustedApps: Collection<String>, autonomous: Boolean) {
        gatePolicy = ApprovalPolicy(autonomous = autonomous, trustedApps = trustedApps)
    }

    override val approval = UiApprovalGateway(pendingApprovals)
    val factory = RunRecordFactory(clock)
    val device = AccessibilityDevice(context)
    override val voice = com.hopa.capabilities.voice.VoiceRecorder(context)
    val speaker = com.hopa.capabilities.voice.Speaker(context)
    /** Goals that run at the same time, each on its own hidden display. */
    override val runner by lazy { com.hopa.app.runtime.ParallelRunner(this) }

    /** Why the last hidden-display start failed; empty when it worked. */
    override var lastBackgroundError: String = ""
        private set


    /**
     * The app's one gateway link. Pairing and the activity life cycle reach it
     * through here; [remoteRunner] is attached below as its delivery handler.
     */
    override val backend: BackendSession by lazy {
        BackendSession(
            settings = settings,
            pairingFactory = { url -> com.hopa.backend.pairing.BackendPairing(url) },
            linkFactory = { credentials, name, version, pushToken, refresh ->
                com.hopa.backend.link.BackendClient(
                    credentials = { credentials },
                    pushToken = pushToken,
                    refresh = refresh,
                    deviceName = name,
                    appVersion = version,
                )
            },
            deviceName = Build.MODEL,
            appVersion = BuildConfig.VERSION_NAME,
            // Without google-services.json Firebase is never initialized, so
            // there is nothing to ask and no token to wait for.
            pushTokenFetch = {
                FirebaseApp.getApps(context).isNotEmpty() && PushTokenFetch.load()
            },
        )
    }

    init {
        // A delivered task runs on the surface the person configured. The
        // runner answers on the socket the delivery arrived on.
        backend.onTask = { task -> remoteRunner.handle(task) }
    }

    /** The surface stack for the person's screenPath setting, shared by chat and remote runs. */
    override val surfaces: suspend (AppSettings) -> ScreenSourceDevice = { Surfaces.resolve(this, it) }

    /** Task ids the gateway delivered, so a redelivery inside a day is skipped. */
    private val processedTasks by lazy { ProcessedTaskStore.open(context) }

    /**
     * A task delivered by the gateway runs on the configured surface and answers
     * on the wake socket. One at a time, so two deliveries never fight over the
     * screen.
     */
    val remoteRunner by lazy {
        RemoteTaskRunner(
            deviceId = { backend.deviceId },
            link = { backend.currentLink() },
            processed = processedTasks,
            settings = { settings.settings.first() },
            surfaces = { current -> surfaces(current) },
            screen = { current -> Surfaces.state(this, current) },
            runGate = runGate,
            foreground = { AppVisibility.foreground },
            startService = { startRunService() },
            stopService = { stopRunService() },
            postApproval = { Notifier.postApproval(context) },
            releaseSurface = { Surfaces.release() },
            run = { request: RemoteRunRequest -> RemoteRuns.execute(this, request) },
            taskStarted = { backend.taskStarted() },
            taskFinished = { backend.taskFinished() },
        )
    }

    private fun startRunService() {
        val intent = android.content.Intent(context, RunService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopRunService() {
        val intent = android.content.Intent(context, RunService::class.java)
        runCatching { context.stopService(intent) }
    }

    /** The hidden background display: one helper process, one loopback port. */
    val displayToken = com.hopa.capabilities.display.client.DisplayToken(context)
    override val shell = com.hopa.capabilities.display.client.ShellClient(displayToken.value)

    /**
     * Wireless debugging inside the app: pair once with the code Android shows,
     * then adbd gives this app shell and no PC is needed.
     */
    val wireless = com.hopa.capabilities.display.client.WirelessShell(context)

    /**
     * A device on the background display, or null when no helper can be reached.
     * The supervisor starts one when nothing answers and reports why when it
     * cannot, so a turn never drives a socket that only looks connected.
     */
    override suspend fun backgroundDevice(): DevicePort? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val status = helper.ensure()
            if (status !is com.hopa.capabilities.display.client.HelperStatus.Running) {
                lastBackgroundError = (status as? com.hopa.capabilities.display.client.HelperStatus.Failed)
                    ?.reason
                    .orEmpty()
                    .ifBlank { "the hidden screen is not running" }
                return@withContext null
            }
            val metrics = context.resources.displayMetrics
            val device = com.hopa.capabilities.display.client.ShellDisplayDevice(shell, context)
            if (device.start(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)) {
                lastBackgroundError = ""
                device
            } else {
                lastBackgroundError = device.error.ifBlank { "the helper refused a display" }
                null
            }
        }

    /**
     * One owner for the helper's life. `connect` heals a stale socket on its own
     * (it pings first), and `start` asks adbd, then the paired PC.
     */
    override val helper = com.hopa.capabilities.display.client.HelperSupervisor(
        ping = { shell.connect() },
        start = { startHelperOnPhone() },
        stop = { shell.close() },
        pause = { kotlinx.coroutines.delay(it) },
    )
    override val helperStatus = helper.status

    /** Ask the phone's own adbd first; fall back to the paired PC. */
    private suspend fun startHelperOnPhone(): Boolean {
        val startedHere = runCatching { wireless.connectAndStartHelper(displayToken.value) }
            .getOrDefault("")
            .contains("ready")
        return startedHere || startHelperThroughBridge()
    }

    /** Pair with this phone's own adbd, then start the helper with that shell. */
    override suspend fun pairWireless(address: String, code: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val host = address.substringBefore(':').trim()
            val port = address.substringAfter(':', "").trim().toIntOrNull()
            if (host.isEmpty() || port == null) {
                return@withContext "Use the address Android shows: ip:port"
            }
            val paired = wireless.pair(host, port, code)
            if (!paired.startsWith("Paired")) {
                return@withContext paired
            }
            val started = wireless.connectAndStartHelper(displayToken.value)
            if (started.contains("ready") && shell.connect()) {
                "Wireless shell is ready; the hidden screen can be used"
            } else {
                started
            }
        }

    /** Ask the paired PC to start the helper on the phone as shell. */
    private suspend fun startHelperThroughBridge(): Boolean {
        val current = settings.settings.first()
        if (current.bridgeUrl.isBlank()) {
            return false
        }
        val token = settings.bridgeToken()
        if (token.isBlank()) {
            return false
        }
        return runCatching {
            com.hopa.bridge.session.BridgeClient(current.bridgeUrl, token)
                .startHelper(token = displayToken.value)
            true
        }.getOrDefault(false)
    }

    val model = SettingsModelPort(settings, ModelFactory(), ProviderChoiceRules())
    /** Shared by the settings screen's "Test the model" button. */
    override val modelFactory: ModelFactory = ModelFactory()

    /** A bridge-backed device when Settings has an address and a token. */
    suspend fun bridgedDevice(): DevicePort? {
        val current = settings.settings.first()
        if (!current.useBridge || current.bridgeUrl.isBlank()) {
            return null
        }
        val token = settings.bridgeToken()
        if (token.isBlank()) {
            return null
        }
        val client = com.hopa.bridge.session.BridgeClient(
            baseUrl = current.bridgeUrl,
            token = token,
            refresh = {
                val saved = settings.bridgeRefresh()
                if (saved.isBlank()) {
                    null
                } else {
                    runCatching {
                        com.hopa.bridge.session.BridgeClient(
                            current.bridgeUrl,
                            "",
                        ).refreshSession(saved)
                    }.getOrNull()?.let { session ->
                        settings.setBridgeToken(session.token)
                        settings.setBridgeRefresh(session.refresh)
                        session.token
                    }
                }
            },
        )
        return com.hopa.bridge.device.AdbDevice(client, clock)
    }

    /**
     * Bring the chat back after a run that happened in another app. Android may
     * refuse a background activity start; the caller posts a notification too, so
     * a refusal still leaves a way in.
     */
    override fun bringToFront() {
        val intent = android.content.Intent(context, com.hopa.app.MainActivity::class.java)
            .addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        runCatching { context.startActivity(intent) }
    }

    /** Read a reply aloud: the PC voice when a bridge is configured, else TTS. */
    override suspend fun speak(text: String): Boolean {
        val current = settings.settings.first()
        val token = settings.bridgeToken()
        if (current.bridgeUrl.isNotBlank() && token.isNotBlank()) {
            val wav = runCatching {
                com.hopa.bridge.session.BridgeClient(current.bridgeUrl, token).speak(text)
            }.getOrNull()
            if (wav != null) {
                return speaker.play(wav)
            }
        }
        return speaker.speak(text)
    }

    /** Speech to text through the PC bridge; null when no bridge is configured. */
    override suspend fun transcribe(wav: ByteArray): String? {
        val current = settings.settings.first()
        if (current.bridgeUrl.isBlank()) {
            return null
        }
        val token = settings.bridgeToken()
        if (token.isBlank()) {
            return null
        }
        return com.hopa.bridge.session.BridgeClient(current.bridgeUrl, token)
            .transcribe(wav)
    }

    /** The conversation half of the bridge, when one is configured. */
    override suspend fun chatSync(): com.hopa.app.link.sync.ChatSync? {
        val current = settings.settings.first()
        if (!current.useBridge || current.bridgeUrl.isBlank()) {
            return null
        }
        val token = settings.bridgeToken()
        if (token.isBlank()) {
            return null
        }
        return com.hopa.app.link.sync.ChatSync(
            conversations = conversations,
            transport = com.hopa.bridge.chat.BridgeChatTransport(
                com.hopa.bridge.session.BridgeClient(current.bridgeUrl, token),
            ),
        )
    }

    /** One executor per device path, sharing the gate snapshot, approval and recorder. */
    fun executorFor(device: DevicePort): ActionExecutor = ActionExecutor(
        device = device,
        browser = null,
        system = AndroidSystemPort(context),
        sandbox = null,
        policy = { gatePolicy },
        approval = approval,
        recorder = recorder,
        factory = factory,
        clock = clock,
    )

    /** Recipes that were replayed before; a matching goal runs without the model. */
    val recipes by lazy { com.hopa.agent.recipe.RecipeStore(java.io.File(context.filesDir, "recipes.txt")) }

    /** The one path from a goal to an answer: chat, parallel runs and deliveries. */
    override val runs by lazy { com.hopa.app.runtime.RunCoordinator(this) }

    /** Asks the backend for a newer knowledge bundle; a failure keeps the copy. */
    val knowledgeUpdater by lazy {
        com.hopa.app.link.sync.CatalogUpdater(backend, knowledgeStore)
    }
}
