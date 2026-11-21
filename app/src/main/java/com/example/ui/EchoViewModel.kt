package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.mesh.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EchoViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getDatabase(application)
    val repository = EchoRepository(db)

    // Real Bluetooth Classic + BLE Mesh, E2EE Cryptography & Audio/Call Engines
    val cryptoService = CryptoService()
    val meshService = BluetoothMeshService(application, cryptoService)
    val audioCallService = AudioCallService(application)

    // Exposed Bluetooth & Mesh StateFlows
    val meshPeers: StateFlow<List<MeshPeer>> = meshService.peers
    val discoveredBtDevices: StateFlow<List<DiscoveredBtDevice>> = meshService.discoveredDevices
    val isScanningDevices: StateFlow<Boolean> = meshService.isScanningDevices
    val relayEvents: StateFlow<List<RelayEvent>> = meshService.relayEvents
    val activeMeshTransfer: StateFlow<MeshTransferProgress?> = meshService.activeTransfer
    val bleHardwareStatus: StateFlow<String> = meshService.bleHardwareStatus
    val isBleRadioActive: StateFlow<Boolean> = meshService.isBleRadioActive
    val isBluetoothEnabled: StateFlow<Boolean> = meshService.isBluetoothEnabled
    val hasBlePermissions: StateFlow<Boolean> = meshService.hasBlePermissions
    val pairingStatusMessage: StateFlow<String?> = meshService.pairingStatusMessage
    val localKeyFingerprint: StateFlow<String> = meshService.localKeyFingerprint
    val localNodeId: StateFlow<String> = meshService.localNodeIdFlow

    // Exposed Audio / Voice Note / PTT StateFlows
    val isRecordingVoiceNote: StateFlow<Boolean> = audioCallService.isRecordingVoiceNote
    val recordingDurationSec: StateFlow<Int> = audioCallService.recordingDurationSec
    val liveWaveformBars: StateFlow<List<Float>> = audioCallService.liveWaveformBars
    val playingVoiceNoteId: StateFlow<Int?> = audioCallService.playingMessageId
    val voiceNoteProgress: StateFlow<Float> = audioCallService.playbackProgress
    val txAudioFrames: StateFlow<Int> = audioCallService.txAudioFrames
    val rxAudioFrames: StateFlow<Int> = audioCallService.rxAudioFrames
    val callAudioLevel: StateFlow<Float> = audioCallService.callAudioLevel
    val isSpeakerOn: StateFlow<Boolean> = audioCallService.isSpeakerOn
    val isPttTransmitting: StateFlow<Boolean> = audioCallService.isPttTransmitting
    val isPttReceiving: StateFlow<Boolean> = audioCallService.isPttReceiving

    // Current logged-in user (MUST stay null on startup — no default auto-login)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Local accounts registered on this device
    val localAccounts: StateFlow<List<User>> = repository.getLocalAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // All known users (local + discovered Bluetooth mesh peers)
    val allUsers = repository.getAllUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current feed
    val feedPosts = _currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getAllPosts()
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Notifications
    val notifications = _currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getNotifications(user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unreadNotificationsCount = _currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getUnreadNotificationCount(user.id)
        } else {
            flowOf(0)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchUserResults = _searchQuery.debounce(300).flatMapLatest { query ->
        if (query.length >= 2) {
            repository.searchUsers(query)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val searchPostResults = _searchQuery.debounce(300).flatMapLatest { query ->
        if (query.length >= 2) {
            repository.searchPosts(query)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Conversations list
    val conversations = repository.getAllConversations().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active conversation detail
    private val _activeConversationId = MutableStateFlow<Int?>(null)
    val activeConversationId = _activeConversationId.asStateFlow()

    val activeMessages = _activeConversationId.flatMapLatest { convId ->
        if (convId != null) {
            repository.getMessages(convId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isPartnerTyping = MutableStateFlow(false)
    val isPartnerTyping = _isPartnerTyping.asStateFlow()

    // Profile viewing
    private val _viewedProfileId = MutableStateFlow<Int?>(null)
    val viewedProfileId = _viewedProfileId.asStateFlow()

    val viewedUser = _viewedProfileId.flatMapLatest { id ->
        if (id != null) {
            repository.getUserByIdFlow(id)
        } else {
            flowOf(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val viewedUserPosts = _viewedProfileId.flatMapLatest { id ->
        if (id != null) {
            repository.getPostsByUserId(id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Call state variables: "idle", "ringing_incoming", "ringing_outgoing", "active"
    private val _callState = MutableStateFlow("idle")
    val callState = _callState.asStateFlow()

    private val _activeCall = MutableStateFlow<CallLog?>(null)
    val activeCall = _activeCall.asStateFlow()

    private val _isCallMuted = MutableStateFlow(false)
    val isCallMuted = _isCallMuted.asStateFlow()

    private val _isCallCameraOn = MutableStateFlow(true)
    val isCallCameraOn = _isCallCameraOn.asStateFlow()

    private val _callDuration = MutableStateFlow(0)
    val callDuration = _callDuration.asStateFlow()

    private val _callTransportMode = MutableStateFlow(MeshCallTransportMode.DIRECT_STREAM)
    val callTransportMode: StateFlow<MeshCallTransportMode> = _callTransportMode.asStateFlow()

    private val _callPeerSafetyNumber = MutableStateFlow("")
    val callPeerSafetyNumber: StateFlow<String> = _callPeerSafetyNumber.asStateFlow()

    private val _lastPttStatus = MutableStateFlow("Hold PTT button to transmit encrypted voice burst")
    val lastPttStatus: StateFlow<String> = _lastPttStatus.asStateFlow()

    private var outgoingCallTimeoutJob: Job? = null

    val callHistory = repository.getCallHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Ensure zero legacy demo accounts remain in local Room DB and NEVER auto-login
        viewModelScope.launch {
            repository.purgeLegacyDemoAccounts()
            meshService.refreshSystemBluetoothState()
        }

        // Persist real Bluetooth discovered peers & bind their Room userId <-> nodeId
        viewModelScope.launch {
            meshService.discoveredPeerProfiles.collect { profile ->
                val savedPeerUser = repository.upsertBluetoothPeerUser(
                    nodeId = profile.nodeId,
                    username = profile.username,
                    bio = profile.bio,
                    publicKeyB64 = profile.publicKeyB64,
                    bluetoothAddress = profile.bluetoothAddress,
                    isPaired = profile.isBonded
                )
                meshService.bindUserIdToNodeId(savedPeerUser.id, profile.nodeId)
                val cur = _currentUser.value
                if (cur != null) {
                    repository.createConversation(listOf(cur.id, savedPeerUser.id))
                }
            }
        }

        // Keep local mesh node synced when user logs in via Bluetooth
        viewModelScope.launch {
            _currentUser.collect { cur ->
                if (cur != null) {
                    val peersSnapshot = repository.getAllUsers().first()
                        .filter { it.id != cur.id && it.nodeId.isNotBlank() }
                        .map { Triple(it.id, it.nodeId, it.publicKeyB64) }
                    meshService.initializeLocalNode(
                        userId = cur.id,
                        nodeId = cur.nodeId,
                        username = cur.username,
                        bio = cur.bio,
                        knownPeerUsers = peersSnapshot
                    )
                    meshService.startBleTransportIfPermitted()
                }
            }
        }

        // Collect decrypted real Bluetooth incoming packets, calls, media & syncs
        viewModelScope.launch {
            meshService.incomingContent.collect { event ->
                val curUser = _currentUser.value ?: return@collect
                when (event) {
                    is IncomingMeshContent.DeliveryAck -> {
                        repository.updateMessageDelivery(
                            packetId = event.originalPacketId,
                            status = "delivered",
                            hopCount = event.hopCount,
                            relayRoute = event.relayRoute
                        )
                    }

                    is IncomingMeshContent.TextMessage -> {
                        val sender = resolveOrCreateSender(event.senderNodeId, event.senderUsername)
                        val convId = repository.createConversation(listOf(curUser.id, sender.id))
                        repository.sendMessage(
                            Message(
                                conversationId = convId,
                                senderId = sender.id,
                                content = event.text,
                                messageType = "text",
                                hopCount = event.hopCount,
                                meshStatus = "delivered",
                                packetId = event.packetId,
                                relayRoute = event.relayRoute,
                                isEncrypted = true,
                                isRead = _activeConversationId.value == convId
                            )
                        )
                    }

                    is IncomingMeshContent.ImageMessage -> {
                        val sender = resolveOrCreateSender(event.senderNodeId, event.senderUsername)
                        val convId = repository.createConversation(listOf(curUser.id, sender.id))
                        val imgFile = java.io.File(
                            getApplication<Application>().cacheDir,
                            "rx_img_${System.currentTimeMillis()}.jpg"
                        ).apply { writeBytes(event.imageBytes) }
                        repository.sendMessage(
                            Message(
                                conversationId = convId,
                                senderId = sender.id,
                                content = "Encrypted Photo (${event.totalChunks} chunks)",
                                messageType = "image",
                                localFilePath = imgFile.absolutePath,
                                hopCount = event.hopCount,
                                meshStatus = "delivered",
                                packetId = event.transferId,
                                relayRoute = event.relayRoute,
                                isEncrypted = true,
                                isRead = _activeConversationId.value == convId
                            )
                        )
                    }

                    is IncomingMeshContent.VoiceNoteMessage -> {
                        val sender = resolveOrCreateSender(event.senderNodeId, event.senderUsername)
                        val convId = repository.createConversation(listOf(curUser.id, sender.id))
                        val vnFile = java.io.File(
                            getApplication<Application>().cacheDir,
                            "rx_vn_${System.currentTimeMillis()}.m4a"
                        ).apply { writeBytes(event.audioBytes) }
                        repository.sendMessage(
                            Message(
                                conversationId = convId,
                                senderId = sender.id,
                                content = "Voice Note (${event.durationSec}s)",
                                messageType = "voice_note",
                                localFilePath = vnFile.absolutePath,
                                audioDurationSec = event.durationSec,
                                waveformJson = event.waveform.joinToString(",") { String.format("%.2f", it) },
                                hopCount = event.hopCount,
                                meshStatus = "delivered",
                                packetId = event.transferId,
                                relayRoute = event.relayRoute,
                                isEncrypted = true,
                                isRead = _activeConversationId.value == convId
                            )
                        )
                    }

                    is IncomingMeshContent.CallSignal -> {
                        handleIncomingCallSignal(curUser, event)
                    }

                    is IncomingMeshContent.DirectAudioFrame -> {
                        if (_callState.value == "active") {
                            audioCallService.playIncomingDirectAudioFrame(event.pcmBytes)
                        }
                    }

                    is IncomingMeshContent.PttVoiceBurst -> {
                        audioCallService.notifyIncomingPttBurst(event.audioBytes, event.durationMs)
                        _lastPttStatus.value = "Received E2EE PTT burst (${event.hopCount} hops)"
                    }

                    is IncomingMeshContent.SyncedPost -> {
                        val sender = resolveOrCreateSender(event.senderNodeId, event.senderUsername)
                        repository.insertPost(
                            Post(
                                userId = sender.id,
                                caption = event.caption,
                                mediaUrls = emptyList(),
                                type = "text",
                                createdAt = event.createdAt
                            )
                        )
                    }

                    is IncomingMeshContent.SyncedNote -> {
                        val sender = resolveOrCreateSender(event.senderNodeId, event.senderUsername)
                        repository.saveNote(sender.id, event.content, event.musicTrack, null)
                    }
                }
            }
        }

        // Call Timer loop
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_callState.value == "active") {
                    _callDuration.value += 1
                }
            }
        }
    }

    private suspend fun resolveOrCreateSender(nodeId: String, username: String): User {
        val existing = repository.getUserByNodeId(nodeId)
        if (existing != null) return existing
        val created = repository.upsertBluetoothPeerUser(
            nodeId = nodeId,
            username = username,
            bio = "Bluetooth Mesh Peer ($nodeId)",
            publicKeyB64 = meshService.getPeerByNodeId(nodeId)?.publicKeyB64 ?: "",
            bluetoothAddress = meshService.getPeerByNodeId(nodeId)?.bluetoothAddress ?: "",
            isPaired = false
        )
        meshService.bindUserIdToNodeId(created.id, nodeId)
        return created
    }

    private suspend fun handleIncomingCallSignal(curUser: User, signal: IncomingMeshContent.CallSignal) {
        val sender = resolveOrCreateSender(signal.senderNodeId, signal.senderUsername)
        when (signal.action) {
            "INVITE_DIRECT", "INVITE_PTT" -> {
                if (_callState.value != "idle") return
                val mode = if (signal.action == "INVITE_DIRECT") {
                    MeshCallTransportMode.DIRECT_STREAM
                } else {
                    MeshCallTransportMode.MULTI_HOP_PTT_BURST
                }
                _callTransportMode.value = mode
                _callPeerSafetyNumber.value = meshService.getSafetyNumberWith(sender.id)
                _lastPttStatus.value = if (mode == MeshCallTransportMode.DIRECT_STREAM) {
                    "Incoming Direct 1-Hop E2EE Voice Call"
                } else {
                    "Incoming ${signal.hopCount}-Hop E2EE Walkie-Talkie PTT"
                }
                _activeCall.value = CallLog(
                    callerId = sender.id,
                    calleeId = curUser.id,
                    callerName = sender.username,
                    calleeName = curUser.username,
                    callerAvatarUrl = sender.avatarUrl,
                    calleeAvatarUrl = curUser.avatarUrl,
                    type = if (mode == MeshCallTransportMode.DIRECT_STREAM) "voice" else "ptt",
                    status = "ringing_incoming",
                    hopCount = signal.hopCount,
                    relayRoute = signal.relayRoute,
                    startedAt = System.currentTimeMillis()
                )
                _callState.value = "ringing_incoming"
            }

            "ACCEPT" -> {
                outgoingCallTimeoutJob?.cancel()
                val call = _activeCall.value ?: return
                if (_callState.value == "ringing_outgoing") {
                    _callState.value = "active"
                    _callDuration.value = 0
                    val partnerId = if (call.callerId == curUser.id) call.calleeId else call.callerId
                    configureActiveCallAudioTransport(partnerId, _callTransportMode.value)
                }
            }

            "DECLINE", "END" -> {
                outgoingCallTimeoutJob?.cancel()
                val call = _activeCall.value ?: return
                val dur = _callDuration.value
                audioCallService.stopDirectAudioStream()
                _callState.value = "idle"
                _activeCall.value = null
                repository.insertCallLog(
                    call.copy(
                        status = if (signal.action == "DECLINE") "declined" else "completed",
                        endedAt = System.currentTimeMillis(),
                        durationSeconds = dur
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // BLUETOOTH-FIRST AUTHENTICATION (NO DEMO ACCOUNTS, NO DEFAULT AUTO-LOGIN)
    // -------------------------------------------------------------------------

    fun getDefaultBluetoothDeviceName(): String = meshService.getDefaultDeviceBluetoothName()

    fun loginWithBluetoothNode(
        usernameOrNodeId: String,
        pinCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val trimmed = usernameOrNodeId.trim()
            if (trimmed.isEmpty()) {
                onError("Enter your Bluetooth Node username.")
                return@launch
            }
            if (!meshService.hasRequiredBlePermissions()) {
                onError("Grant Bluetooth permissions first to log in.")
                return@launch
            }
            if (!meshService.isBluetoothEnabled.value) {
                onError("Turn on Bluetooth first to activate your mesh node.")
                return@launch
            }

            val user = (repository.getUserByUsername(trimmed) ?: repository.getUserByNodeId(trimmed))
                ?.takeIf { it.isLocalAccount }

            if (user == null) {
                onError("No local Bluetooth node found for '$trimmed'. Create a node below.")
                return@launch
            }
            if (user.passwordHash.isNotBlank() && user.passwordHash != pinCode.trim()) {
                onError("Incorrect PIN / passcode for @$trimmed.")
                return@launch
            }

            _currentUser.value = user
            onSuccess()
        }
    }

    fun selectAndLoginLocalAccount(
        user: User,
        pinCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (!meshService.hasRequiredBlePermissions()) {
                onError("Grant Bluetooth permissions first to log in.")
                return@launch
            }
            if (!meshService.isBluetoothEnabled.value) {
                onError("Turn on Bluetooth first to log in.")
                return@launch
            }
            if (user.passwordHash.isNotBlank() && user.passwordHash != pinCode.trim()) {
                onError("Incorrect PIN for @${user.username}.")
                return@launch
            }
            _currentUser.value = user
            onSuccess()
        }
    }

    fun createBluetoothAccount(
        username: String,
        pinCode: String,
        bio: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val cleanUsername = username.trim().replace(" ", "_")
            if (cleanUsername.length < 2) {
                onError("Username must be at least 2 characters.")
                return@launch
            }
            if (!meshService.hasRequiredBlePermissions()) {
                onError("Grant Bluetooth permissions first to create your node.")
                return@launch
            }
            if (!meshService.isBluetoothEnabled.value) {
                onError("Turn on Bluetooth first to register your mesh node.")
                return@launch
            }
            val existing = repository.getUserByUsername(cleanUsername)
            if (existing != null && existing.isLocalAccount) {
                onError("A local Bluetooth node named @$cleanUsername already exists.")
                return@launch
            }

            val nodeId = if (localAccounts.value.isEmpty()) {
                meshService.localNodeIdFlow.value
            } else {
                meshService.generateNewNodeId()
            }
            meshService.loadOrCreateKeyPair(nodeId)
            val pubB64 = meshService.getPublicKeyB64(nodeId)

            val newUser = User(
                username = cleanUsername,
                email = "$nodeId@bluetooth.mesh",
                passwordHash = pinCode.trim(),
                bio = bio.trim().ifBlank { "Bluetooth Mesh Node • $nodeId" },
                nodeId = nodeId,
                publicKeyB64 = pubB64,
                isLocalAccount = true,
                isPaired = true,
                isVerified = true
            )
            val newId = repository.insertUser(newUser)
            _currentUser.value = newUser.copy(id = newId)
            onSuccess()
        }
    }

    fun deleteLocalAccount(userId: Int) {
        viewModelScope.launch {
            if (_currentUser.value?.id == userId) {
                logout()
            }
            repository.deleteUser(userId)
        }
    }

    // -------------------------------------------------------------------------
    // BLUETOOTH DISCOVERY, PAIRING & CONNECTION CONTROLS
    // -------------------------------------------------------------------------

    fun refreshBlePermissionsAndRadio() {
        meshService.refreshSystemBluetoothState()
        if (_currentUser.value != null) {
            meshService.startBleTransportIfPermitted()
        }
    }

    fun startBluetoothDiscovery() {
        meshService.startDiscoveryScan(includeClassicDiscovery = true)
    }

    fun stopBluetoothDiscovery() {
        meshService.stopDiscoveryScan()
    }

    fun pairBluetoothDevice(address: String) {
        meshService.pairDevice(address)
    }

    fun connectBluetoothDevice(address: String) {
        meshService.connectToDevice(address)
    }

    fun disconnectBluetoothDevice(address: String) {
        meshService.disconnectDevice(address)
    }

    fun unpairBluetoothDevice(address: String) {
        meshService.unpairDevice(address)
    }

    // -------------------------------------------------------------------------
    // SOCIAL PREFERENCES & ACCOUNT MANAGEMENT
    // -------------------------------------------------------------------------

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode = _isDarkMode.asStateFlow()

    private val _hideReactionCounts = MutableStateFlow(false)
    val hideReactionCounts = _hideReactionCounts.asStateFlow()

    private val _isProfileLocked = MutableStateFlow(false)
    val isProfileLocked = _isProfileLocked.asStateFlow()

    private val _showActiveStatus = MutableStateFlow(true)
    val showActiveStatus = _showActiveStatus.asStateFlow()

    private val _dataSaverEnabled = MutableStateFlow(false)
    val dataSaverEnabled = _dataSaverEnabled.asStateFlow()

    private val _appLanguage = MutableStateFlow("English (US)")
    val appLanguage = _appLanguage.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleHideReactions() {
        _hideReactionCounts.value = !_hideReactionCounts.value
    }

    fun toggleProfileLock() {
        val user = _currentUser.value ?: return
        val newLocked = !_isProfileLocked.value
        _isProfileLocked.value = newLocked
        viewModelScope.launch {
            val updated = user.copy(isPrivate = newLocked)
            repository.updateUser(updated)
            _currentUser.value = updated
        }
    }

    fun toggleActiveStatus() {
        _showActiveStatus.value = !_showActiveStatus.value
    }

    fun toggleDataSaver() {
        _dataSaverEnabled.value = !_dataSaverEnabled.value
    }

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
    }

    fun logout() {
        outgoingCallTimeoutJob?.cancel()
        audioCallService.stopDirectAudioStream()
        _callState.value = "idle"
        _activeCall.value = null
        _activeConversationId.value = null
        meshService.clearSession()
        _currentUser.value = null
    }

    fun updateProfile(bio: String, isPrivate: Boolean) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(bio = bio, isPrivate = isPrivate)
            repository.updateUser(updated)
            _currentUser.value = updated
        }
    }

    fun updateUserAvatarAndCover(avatarUrl: String, coverUrl: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(
                avatarUrl = if (avatarUrl.isNotBlank()) avatarUrl else user.avatarUrl,
                coverUrl = if (coverUrl.isNotBlank()) coverUrl else user.coverUrl
            )
            repository.updateUser(updated)
            _currentUser.value = updated
        }
    }

    fun setViewedProfile(userId: Int) {
        _viewedProfileId.value = userId
    }

    fun toggleFollow(targetUserId: Int) {
        val curUser = _currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleFollow(curUser.id, targetUserId)
        }
    }

    // Post creation (also syncs over Bluetooth mesh!)
    fun createPost(caption: String, imageUrl: String, type: String = "image") {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val mediaList = if (imageUrl.isNotEmpty()) listOf(imageUrl) else emptyList()
            val post = Post(
                userId = user.id,
                caption = caption,
                mediaUrls = mediaList,
                type = if (mediaList.isEmpty()) "text" else type
            )
            repository.insertPost(post)
            meshService.broadcastSocialPost(caption)
        }
    }

    fun deletePost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post)
        }
    }

    fun toggleLikePost(postId: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleLikePost(user.id, postId)
        }
    }

    fun toggleLikeComment(commentId: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleLikeComment(user.id, commentId)
        }
    }

    fun addComment(postId: Int, content: String, parentCommentId: Int? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val comment = Comment(
                postId = postId,
                userId = user.id,
                content = content,
                parentCommentId = parentCommentId
            )
            repository.insertComment(comment)
        }
    }

    fun getCommentsForPost(postId: Int): Flow<List<Comment>> = repository.getCommentsForPost(postId)
    fun getLikeCountForPost(postId: Int): Flow<Int> = repository.getLikeCountForPost(postId)
    fun getCommentCountForPost(postId: Int): Flow<Int> = repository.getCommentCountForPost(postId)

    fun isPostLikedByUser(postId: Int): Flow<Boolean> {
        val user = _currentUser.value ?: return flowOf(false)
        return repository.isPostLikedByUser(user.id, postId)
    }

    fun isFollowing(userId: Int): Flow<Boolean> {
        val user = _currentUser.value ?: return flowOf(false)
        return repository.isFollowing(user.id, userId)
    }

    fun getFollowCounts(userId: Int): Flow<Pair<Int, Int>> {
        return combine(
            repository.getFollowerCount(userId),
            repository.getFollowingCount(userId)
        ) { followers, following -> Pair(followers, following) }
    }

    fun isCommentLikedByUser(commentId: Int): Flow<Boolean> {
        val user = _currentUser.value ?: return flowOf(false)
        return repository.isCommentLikedByUser(user.id, commentId)
    }

    fun getLikeCountForComment(commentId: Int): Flow<Int> = repository.getLikeCountForComment(commentId)
    fun getLastMessage(conversationId: Int): Flow<Message?> = repository.getLastMessage(conversationId)
    suspend fun getConversationById(id: Int): Conversation? = repository.getConversationById(id)

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    // -------------------------------------------------------------------------
    // MESSAGING FUNCTIONS OVER REAL BLUETOOTH MESH
    // -------------------------------------------------------------------------

    fun selectConversation(conversationId: Int) {
        _activeConversationId.value = conversationId
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.markMessagesAsRead(conversationId, user.id)
        }
    }

    fun clearActiveConversation() {
        _activeConversationId.value = null
    }

    fun startConversationWith(targetUserId: Int, onComplete: (Int) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val convId = repository.createConversation(listOf(user.id, targetUserId))
            onComplete(convId)
        }
    }

    fun sendChatMessage(content: String, mediaUrl: String? = null) {
        val user = _currentUser.value ?: return
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val conv = repository.getConversationById(convId) ?: return@launch
            val otherId = conv.participantIds.firstOrNull { it != user.id } ?: return@launch

            meshService.sendEncryptedText(
                recipientId = otherId,
                text = content
            ) { packetId, hopDistance, routeLabel, sentOverBt ->
                viewModelScope.launch {
                    val msg = Message(
                        conversationId = convId,
                        senderId = user.id,
                        content = content,
                        mediaUrl = mediaUrl,
                        messageType = "text",
                        hopCount = hopDistance,
                        meshStatus = when {
                            !sentOverBt -> "offline"
                            hopDistance == 1 -> "encrypting"
                            else -> "relaying"
                        },
                        packetId = packetId,
                        relayRoute = routeLabel,
                        isEncrypted = true
                    )
                    repository.sendMessage(msg)
                }
            }
        }
    }

    fun sendPhotoMessage(imageUri: Uri) {
        val user = _currentUser.value ?: return
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val conv = repository.getConversationById(convId) ?: return@launch
            val otherId = conv.participantIds.firstOrNull { it != user.id } ?: return@launch

            meshService.sendEncryptedImage(
                recipientId = otherId,
                imageUri = imageUri
            ) { transferId, savedPath, hopDistance, routeLabel, summary, sentOverBt ->
                viewModelScope.launch {
                    repository.sendMessage(
                        Message(
                            conversationId = convId,
                            senderId = user.id,
                            content = summary,
                            messageType = "image",
                            localFilePath = savedPath,
                            hopCount = hopDistance,
                            meshStatus = if (sentOverBt) "relaying" else "offline",
                            packetId = transferId,
                            relayRoute = routeLabel,
                            isEncrypted = true
                        )
                    )
                }
            }
        }
    }

    fun startVoiceNoteRecording() {
        audioCallService.startVoiceNoteRecording()
    }

    fun stopAndSendVoiceNote(cancel: Boolean = false) {
        val recorded = audioCallService.stopVoiceNoteRecording(cancel = cancel) ?: return
        val user = _currentUser.value ?: return
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val conv = repository.getConversationById(convId) ?: return@launch
            val otherId = conv.participantIds.firstOrNull { it != user.id } ?: return@launch

            meshService.sendEncryptedVoiceNote(
                recipientId = otherId,
                audioBytes = recorded.audioBytes,
                durationSec = recorded.durationSec,
                waveform = recorded.waveform
            ) { transferId, hopDistance, routeLabel, sentOverBt ->
                viewModelScope.launch {
                    repository.sendMessage(
                        Message(
                            conversationId = convId,
                            senderId = user.id,
                            content = "Voice Note (${recorded.durationSec}s)",
                            messageType = "voice_note",
                            localFilePath = recorded.filePath,
                            audioDurationSec = recorded.durationSec,
                            waveformJson = recorded.waveform.joinToString(",") { String.format("%.2f", it) },
                            hopCount = hopDistance,
                            meshStatus = if (sentOverBt) "relaying" else "offline",
                            packetId = transferId,
                            relayRoute = routeLabel,
                            isEncrypted = true
                        )
                    )
                }
            }
        }
    }

    fun togglePlayVoiceNote(msg: Message) {
        audioCallService.togglePlayVoiceNote(
            messageId = msg.id,
            filePath = msg.localFilePath,
            fallbackDurationSec = msg.audioDurationSec.coerceAtLeast(2)
        )
    }

    fun getSafetyNumberForUser(userId: Int): String = meshService.getSafetyNumberWith(userId)

    // -------------------------------------------------------------------------
    // REAL BLUETOOTH VOICE CALLING & MULTI-HOP PTT WALKIE-TALKIE
    // -------------------------------------------------------------------------

    fun initiateCall(targetUserId: Int, type: String = "voice") {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val target = repository.getUserById(targetUserId) ?: return@launch
            val peer = meshService.getPeer(targetUserId) ?: return@launch
            if (peer.hopDistance <= 0) return@launch

            val hopCount = peer.hopDistance.coerceAtLeast(1)
            val transportMode = if (hopCount == 1) {
                MeshCallTransportMode.DIRECT_STREAM
            } else {
                MeshCallTransportMode.MULTI_HOP_PTT_BURST
            }
            val routeLabel = peer.routePath.joinToString(" → ").ifEmpty { "${user.username} → ${target.username}" }

            _callTransportMode.value = transportMode
            _callPeerSafetyNumber.value = meshService.getSafetyNumberWith(targetUserId)
            _lastPttStatus.value = if (transportMode == MeshCallTransportMode.DIRECT_STREAM) {
                "Ringing via Direct 1-Hop Bluetooth..."
            } else {
                "Ringing via $hopCount-Hop Mesh PTT..."
            }

            val call = CallLog(
                callerId = user.id,
                calleeId = targetUserId,
                callerName = user.username,
                calleeName = target.username,
                callerAvatarUrl = user.avatarUrl,
                calleeAvatarUrl = target.avatarUrl,
                type = if (transportMode == MeshCallTransportMode.DIRECT_STREAM) "voice" else "ptt",
                status = "ringing_outgoing",
                hopCount = hopCount,
                relayRoute = routeLabel,
                startedAt = System.currentTimeMillis()
            )
            _activeCall.value = call
            _callState.value = "ringing_outgoing"

            meshService.sendCallSignal(
                recipientId = targetUserId,
                action = if (hopCount == 1) "INVITE_DIRECT" else "INVITE_PTT",
                callType = call.type
            )

            // Wait up to 35s for real remote Bluetooth peer to tap Accept
            outgoingCallTimeoutJob?.cancel()
            outgoingCallTimeoutJob = viewModelScope.launch {
                delay(35_000L)
                if (_callState.value == "ringing_outgoing") {
                    endActiveCall(statusOverride = "missed")
                }
            }
        }
    }

    fun acceptIncomingCall() {
        val call = _activeCall.value ?: return
        val curUserId = _currentUser.value?.id ?: return
        val partnerId = if (call.callerId == curUserId) call.calleeId else call.callerId

        meshService.sendCallSignal(partnerId, "ACCEPT", call.type)
        _callState.value = "active"
        _callDuration.value = 0
        configureActiveCallAudioTransport(partnerId, _callTransportMode.value)
    }

    private fun configureActiveCallAudioTransport(partnerId: Int, mode: MeshCallTransportMode) {
        if (mode == MeshCallTransportMode.DIRECT_STREAM) {
            audioCallService.startDirectAudioStream(
                isMutedProvider = { _isCallMuted.value },
                onEncodedFrameReady = { pcmBytes, seq ->
                    meshService.sendDirectCallAudioFrame(partnerId, pcmBytes, seq)
                }
            )
        } else {
            audioCallService.stopDirectAudioStream()
            _lastPttStatus.value = "Channel Clear • Hold PTT to transmit"
        }
    }

    fun startPttTransmit() {
        if (_callState.value != "active") return
        _lastPttStatus.value = "Recording & encrypting voice burst..."
        audioCallService.startPttBurst()
    }

    fun releasePttTransmit() {
        val burst = audioCallService.releasePttBurst() ?: return
        val call = _activeCall.value ?: return
        val curUserId = _currentUser.value?.id
        val partnerId = if (call.callerId == curUserId) call.calleeId else call.callerId
        val (bytes, durationMs) = burst
        if (bytes.isEmpty()) {
            _lastPttStatus.value = "Grant Mic permission to transmit PTT voice"
            return
        }
        _lastPttStatus.value = "Relaying ${bytes.size}B burst (${call.hopCount} Hops)..."
        val sent = meshService.sendPttVoiceBurst(partnerId, bytes, durationMs)
        _lastPttStatus.value = if (sent) {
            "Burst sent over Bluetooth (${call.hopCount}H) • Hold PTT to reply"
        } else {
            "Peer unreachable over Bluetooth"
        }
    }

    fun toggleSpeakerphone() = audioCallService.toggleSpeakerphone()

    fun declineIncomingCall() {
        outgoingCallTimeoutJob?.cancel()
        val call = _activeCall.value ?: return
        val curUserId = _currentUser.value?.id
        val partnerId = if (call.callerId == curUserId) call.calleeId else call.callerId
        meshService.sendCallSignal(partnerId, "DECLINE", call.type)
        audioCallService.stopDirectAudioStream()
        _callState.value = "idle"
        _activeCall.value = null
        viewModelScope.launch {
            repository.insertCallLog(
                call.copy(status = "declined", endedAt = System.currentTimeMillis(), durationSeconds = 0)
            )
        }
    }

    fun endActiveCall(statusOverride: String = "completed") {
        outgoingCallTimeoutJob?.cancel()
        val call = _activeCall.value ?: return
        val duration = _callDuration.value
        val curUserId = _currentUser.value?.id
        val partnerId = if (call.callerId == curUserId) call.calleeId else call.callerId
        meshService.sendCallSignal(partnerId, "END", call.type)
        audioCallService.stopDirectAudioStream()
        _callState.value = "idle"
        _activeCall.value = null
        viewModelScope.launch {
            repository.insertCallLog(
                call.copy(status = statusOverride, endedAt = System.currentTimeMillis(), durationSeconds = duration)
            )
        }
    }

    fun toggleCallMute() {
        _isCallMuted.value = !_isCallMuted.value
    }

    fun toggleCallCamera() {
        _isCallCameraOn.value = !_isCallCameraOn.value
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    // Audio Player System for Notes & Reels
    private var mediaPlayer: android.media.MediaPlayer? = null
    private val _currentlyPlayingTrack = MutableStateFlow<String?>(null)
    val currentlyPlayingTrack = _currentlyPlayingTrack.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio = _isPlayingAudio.asStateFlow()

    fun playAudioTrack(audioUrl: String, trackName: String) {
        if (audioUrl.isBlank()) return
        viewModelScope.launch {
            try {
                if (_currentlyPlayingTrack.value == trackName && _isPlayingAudio.value) {
                    stopAudioTrack()
                    return@launch
                }
                stopAudioTrack()
                mediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(audioUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        _currentlyPlayingTrack.value = trackName
                        _isPlayingAudio.value = true
                    }
                    setOnCompletionListener {
                        _currentlyPlayingTrack.value = null
                        _isPlayingAudio.value = false
                    }
                    setOnErrorListener { _, _, _ ->
                        _currentlyPlayingTrack.value = null
                        _isPlayingAudio.value = false
                        true
                    }
                }
            } catch (_: Exception) {
                _currentlyPlayingTrack.value = null
                _isPlayingAudio.value = false
            }
        }
    }

    fun stopAudioTrack() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            _currentlyPlayingTrack.value = null
            _isPlayingAudio.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioTrack()
        audioCallService.release()
        meshService.release()
    }

    // Reels System
    val allReels = repository.getAllReels().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun createReel(caption: String, videoUrl: String, audioTrackName: String = "Original Audio", audioUrl: String? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.insertReel(
                Reel(
                    userId = user.id,
                    caption = caption,
                    videoUrl = videoUrl,
                    audioTrackName = audioTrackName,
                    audioUrl = audioUrl
                )
            )
        }
    }

    fun toggleLikeReel(reelId: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleLikeReel(user.id, reelId)
        }
    }

    fun isReelLikedByUser(reelId: Int) = repository.isReelLikedByUser(_currentUser.value?.id ?: -1, reelId)
    fun getReelLikesCount(reelId: Int) = repository.getReelLikesCount(reelId)

    // Notes System (broadcasts over Bluetooth mesh!)
    val allNotes = repository.getAllNotes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveNote(content: String, musicTrack: String? = null, audioUrl: String? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.saveNote(user.id, content, musicTrack, audioUrl)
            meshService.broadcastUserNote(content, musicTrack)
        }
    }

    fun deleteNote() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteNote(user.id)
        }
    }

    fun replyToNote(targetUserId: Int, noteContent: String, replyMessage: String, onComplete: () -> Unit) {
        if (replyMessage.isBlank()) return
        viewModelScope.launch {
            startConversationWith(targetUserId) { convId ->
                selectConversation(convId)
                val fullMessage = "Replying to note: \"$noteContent\"\n\n$replyMessage"
                sendChatMessage(fullMessage)
                onComplete()
            }
        }
    }

    fun markAllNotificationsAsRead() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.markAllNotificationsAsRead(user.id)
        }
    }
}
