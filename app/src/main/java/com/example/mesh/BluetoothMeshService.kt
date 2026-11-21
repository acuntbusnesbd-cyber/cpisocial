package com.example.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class MeshPacketType {
    TEXT,
    IMAGE_CHUNK,
    VOICE_NOTE_CHUNK,
    CALL_SIGNAL,
    CALL_AUDIO_FRAME,
    PTT_VOICE_BURST,
    ACK
}

enum class MeshRouteMode {
    DIRECT_1_HOP,
    MULTI_HOP_RELAY,
    OFFLINE
}

/**
 * Routing header is unencrypted so intermediate relay phones can forward packets
 * without ever seeing or decrypting [encryptedPayload].
 */
data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString().take(12),
    val senderNodeId: String,
    val senderUsername: String,
    val recipientNodeId: String,
    val ttl: Int = 6,
    val maxTtl: Int = 6,
    val hopCount: Int = 0,
    val packetType: MeshPacketType,
    val transferId: String? = null,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val metadataJson: String = "",
    val relayPath: List<String> = emptyList(),
    val encryptedPayload: CryptoService.EncryptedEnvelope,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun aadContext(): String = "$packetId|$senderNodeId|$recipientNodeId|${packetType.name}|$chunkIndex/$totalChunks"
}

data class MeshPeer(
    val userId: Int,
    val username: String,
    val nodeId: String,
    val bluetoothAddress: String = "",
    val publicKeyB64: String,
    val keyFingerprint: String,
    val hopDistance: Int, // 1 = Direct BT, 2..6 = Multi-hop relay, -1 = Offline
    val rssi: Int,
    val nextHopNodeId: String? = null,
    val nextHopAddress: String? = null,
    val nextHopUsername: String? = null,
    val routePath: List<String> = emptyList(),
    val isHardwareBle: Boolean = true,
    val transportLabel: String = "Bluetooth"
) {
    val routeMode: MeshRouteMode
        get() = when {
            hopDistance == 1 -> MeshRouteMode.DIRECT_1_HOP
            hopDistance >= 2 -> MeshRouteMode.MULTI_HOP_RELAY
            else -> MeshRouteMode.OFFLINE
        }

    val hopLabel: String
        get() = when {
            hopDistance == 1 -> "Direct • 1 Hop"
            hopDistance >= 2 -> "Mesh • $hopDistance Hops"
            else -> "Offline"
        }
}

data class DiscoveredBtDevice(
    val address: String,
    val name: String,
    val rssi: Int = -65,
    val bondState: Int = BluetoothDevice.BOND_NONE,
    val isMeshCapable: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val transportType: String = "Bluetooth",
    val pairedNodeId: String? = null,
    val pairedUsername: String? = null,
    val lastSeenAt: Long = System.currentTimeMillis()
) {
    val isBonded: Boolean get() = bondState == BluetoothDevice.BOND_BONDED
    val isBonding: Boolean get() = bondState == BluetoothDevice.BOND_BONDING
}

data class DiscoveredPeerProfile(
    val nodeId: String,
    val username: String,
    val bio: String,
    val publicKeyB64: String,
    val bluetoothAddress: String,
    val isBonded: Boolean,
    val hopDistance: Int,
    val rssi: Int,
    val nextHopNodeId: String?,
    val nextHopAddress: String?,
    val nextHopUsername: String?,
    val routePath: List<String>,
    val transportLabel: String
)

data class RelayEvent(
    val id: String = UUID.randomUUID().toString().take(8),
    val packetId: String,
    val packetType: MeshPacketType,
    val relayNodeName: String,
    val senderName: String,
    val targetUsername: String,
    val hopNumber: Int,
    val ttlRemaining: Int,
    val ciphertextSnippet: String,
    val relayCanDecrypt: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeshTransferProgress(
    val transferId: String,
    val recipientId: Int,
    val packetType: MeshPacketType,
    val sentChunks: Int,
    val totalChunks: Int,
    val hopDistance: Int,
    val compressedBytes: Int,
    val originalBytes: Int
)

sealed class IncomingMeshContent {
    data class TextMessage(
        val packetId: String,
        val senderNodeId: String,
        val senderUsername: String,
        val text: String,
        val hopCount: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class ImageMessage(
        val transferId: String,
        val senderNodeId: String,
        val senderUsername: String,
        val imageBytes: ByteArray,
        val hopCount: Int,
        val totalChunks: Int,
        val compressedSize: Int,
        val originalSize: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class VoiceNoteMessage(
        val transferId: String,
        val senderNodeId: String,
        val senderUsername: String,
        val audioBytes: ByteArray,
        val durationSec: Int,
        val waveform: List<Float>,
        val hopCount: Int,
        val totalChunks: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class DeliveryAck(
        val originalPacketId: String,
        val fromNodeId: String,
        val hopCount: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class CallSignal(
        val senderNodeId: String,
        val senderUsername: String,
        val action: String, // "INVITE_DIRECT", "INVITE_PTT", "ACCEPT", "DECLINE", "END"
        val callType: String,
        val hopCount: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class DirectAudioFrame(
        val senderNodeId: String,
        val pcmBytes: ByteArray,
        val sequence: Int
    ) : IncomingMeshContent()

    data class PttVoiceBurst(
        val senderNodeId: String,
        val audioBytes: ByteArray,
        val durationMs: Int,
        val hopCount: Int,
        val relayRoute: String
    ) : IncomingMeshContent()

    data class SyncedPost(
        val senderNodeId: String,
        val senderUsername: String,
        val caption: String,
        val createdAt: Long
    ) : IncomingMeshContent()

    data class SyncedNote(
        val senderNodeId: String,
        val senderUsername: String,
        val content: String,
        val musicTrack: String?
    ) : IncomingMeshContent()
}

/**
 * Real Android Bluetooth Classic + BLE GATT Multi-Hop Mesh Service.
 *
 * - Classic Bluetooth Discovery (ACTION_FOUND) + Bonding (createBond / ACTION_BOND_STATE_CHANGED)
 * - Bluetooth RFCOMM Server + Client Socket streaming (length-prefixed JSON wire frames)
 * - BLE Advertising + Scanning (ECHO_MESH_SERVICE_UUID) + BLE GATT Server/Client chunked transport
 * - Dynamic Multi-Hop Routing Table learned via HELLO / HELLO_ACK neighbor advertisements
 * - End-to-End Encryption (ECDH P-256 + HKDF-SHA256 + AES-256-GCM) with Zero-Knowledge Relay forwarding
 */
class BluetoothMeshService(
    private val context: Context,
    private val cryptoService: CryptoService
) {
    companion object {
        private const val TAG = "EchoBleMesh"
        private const val PREFS_NAME = "cpi_mesh_identity"
        val MESH_RFCOMM_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val ECHO_MESH_SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000EC40-0000-1000-8000-00805F9B34FB")
        val ECHO_MESH_CHAR_RX_UUID: UUID =
            UUID.fromString("0000EC41-0000-1000-8000-00805F9B34FB")
        val ECHO_MESH_CHAR_TX_UUID: UUID =
            UUID.fromString("0000EC42-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val BLE_CHUNK_BYTES = 450
        private const val GATT_FRAME_MTU = 180
        private const val MAX_SEEN_PACKETS = 600
        private const val MAX_TTL = 6
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Persistent Local Node Identity & ECDH KeyPair
    private var localUserId: Int = -1
    private var localNodeId: String = getOrCreateDefaultNodeId()
    private var localUsername: String = ""
    private var localBio: String = ""
    private var localKeyPair: KeyPair = loadOrCreateKeyPair(localNodeId)

    // Loop prevention cache (packetId -> timestamp)
    private val seenPacketCache = object : LinkedHashMap<String, Long>(MAX_SEEN_PACKETS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_SEEN_PACKETS
        }
    }

    // Chunk assembly buffers
    private val chunkAssemblyBuffer = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()
    private val chunkMetadataBuffer = ConcurrentHashMap<String, String>()
    private val gattWireAssembly = ConcurrentHashMap<String, ConcurrentHashMap<Int, ByteArray>>()

    // Active transport links keyed by remote Bluetooth MAC address
    private class RfcommLink(
        val address: String,
        val socket: BluetoothSocket,
        val output: DataOutputStream,
        val readerJob: Job
    )

    private val rfcommLinks = ConcurrentHashMap<String, RfcommLink>()
    private val gattClientLinks = ConcurrentHashMap<String, BluetoothGatt>()
    private val gattClientRxChars = ConcurrentHashMap<String, BluetoothGattCharacteristic>()
    private val gattServerSubscribedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private var gattTxCharacteristic: BluetoothGattCharacteristic? = null

    // Routing tables: nodeId <-> address
    private val addressToNodeId = ConcurrentHashMap<String, String>()
    private val nodeIdToPeer = ConcurrentHashMap<String, MeshPeer>()
    private val userIdToNodeId = ConcurrentHashMap<Int, String>()

    // Reactive StateFlows
    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBtDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBtDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices.asStateFlow()

    private val _relayEvents = MutableStateFlow<List<RelayEvent>>(emptyList())
    val relayEvents: StateFlow<List<RelayEvent>> = _relayEvents.asStateFlow()

    private val _activeTransfer = MutableStateFlow<MeshTransferProgress?>(null)
    val activeTransfer: StateFlow<MeshTransferProgress?> = _activeTransfer.asStateFlow()

    private val _bleHardwareStatus = MutableStateFlow("Checking Bluetooth hardware...")
    val bleHardwareStatus: StateFlow<String> = _bleHardwareStatus.asStateFlow()

    private val _isBleRadioActive = MutableStateFlow(false)
    val isBleRadioActive: StateFlow<Boolean> = _isBleRadioActive.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _hasBlePermissions = MutableStateFlow(false)
    val hasBlePermissions: StateFlow<Boolean> = _hasBlePermissions.asStateFlow()

    private val _pairingStatusMessage = MutableStateFlow<String?>(null)
    val pairingStatusMessage: StateFlow<String?> = _pairingStatusMessage.asStateFlow()

    private val _localKeyFingerprint = MutableStateFlow(
        cryptoService.getKeyFingerprint(cryptoService.encodePublicKey(localKeyPair.public))
    )
    val localKeyFingerprint: StateFlow<String> = _localKeyFingerprint.asStateFlow()

    private val _localNodeIdFlow = MutableStateFlow(localNodeId)
    val localNodeIdFlow: StateFlow<String> = _localNodeIdFlow.asStateFlow()

    private val _incomingContent = MutableSharedFlow<IncomingMeshContent>(extraBufferCapacity = 128)
    val incomingContent: SharedFlow<IncomingMeshContent> = _incomingContent.asSharedFlow()

    private val _discoveredPeerProfiles = MutableSharedFlow<DiscoveredPeerProfile>(extraBufferCapacity = 64)
    val discoveredPeerProfiles: SharedFlow<DiscoveredPeerProfile> = _discoveredPeerProfiles.asSharedFlow()

    // Hardware Bluetooth components
    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var rfcommServerSocket: BluetoothServerSocket? = null
    private var rfcommAcceptJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var isBleScanning = false
    private var isAdvertising = false
    private var isReceiverRegistered = false

    init {
        registerBluetoothReceiver()
        refreshSystemBluetoothState()
    }

    // -------------------------------------------------------------------------
    // 1. PERSISTENT NODE IDENTITY & ECDH KEYPAIR
    // -------------------------------------------------------------------------

    private fun getOrCreateDefaultNodeId(): String {
        val saved = prefs.getString("default_node_id", null)
        if (!saved.isNullOrBlank()) return saved
        val generated = "CPI-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
        prefs.edit().putString("default_node_id", generated).apply()
        return generated
    }

    fun generateNewNodeId(): String {
        return "CPI-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase()}"
    }

    fun loadOrCreateKeyPair(nodeId: String): KeyPair {
        val privB64 = prefs.getString("priv_$nodeId", null)
        val pubB64 = prefs.getString("pub_$nodeId", null)
        if (!privB64.isNullOrBlank() && !pubB64.isNullOrBlank()) {
            runCatching {
                val priv = cryptoService.decodePrivateKey(privB64)
                val pub = cryptoService.decodePublicKey(pubB64)
                return KeyPair(pub, priv)
            }
        }
        val kp = cryptoService.generateIdentityKeyPair()
        prefs.edit()
            .putString("priv_$nodeId", cryptoService.encodePrivateKey(kp.private))
            .putString("pub_$nodeId", cryptoService.encodePublicKey(kp.public))
            .apply()
        return kp
    }

    fun getPublicKeyB64(nodeId: String = localNodeId): String {
        val kp = if (nodeId == localNodeId) localKeyPair else loadOrCreateKeyPair(nodeId)
        return cryptoService.encodePublicKey(kp.public)
    }

    fun getDefaultDeviceBluetoothName(): String {
        return try {
            if (hasConnectPermission()) {
                bluetoothAdapter?.name?.takeIf { it.isNotBlank() } ?: Build.MODEL
            } else {
                Build.MODEL
            }
        } catch (_: Exception) {
            Build.MODEL
        }
    }

    fun initializeLocalNode(
        userId: Int,
        nodeId: String,
        username: String,
        bio: String,
        knownPeerUsers: List<Triple<Int, String, String>> // (userId, nodeId, publicKeyB64) + optional map
    ) {
        val resolvedNodeId = nodeId.ifBlank { localNodeId }
        localUserId = userId
        localNodeId = resolvedNodeId
        localUsername = username
        localBio = bio
        localKeyPair = loadOrCreateKeyPair(resolvedNodeId)

        _localNodeIdFlow.value = resolvedNodeId
        _localKeyFingerprint.value = cryptoService.getKeyFingerprint(
            cryptoService.encodePublicKey(localKeyPair.public)
        )

        knownPeerUsers.forEach { (uId, nId, _) ->
            if (nId.isNotBlank() && uId != userId) {
                userIdToNodeId[uId] = nId
                nodeIdToPeer[nId]?.let { existing ->
                    nodeIdToPeer[nId] = existing.copy(userId = uId)
                }
            }
        }
        publishPeersSnapshot()
    }

    fun bindUserIdToNodeId(userId: Int, nodeId: String) {
        userIdToNodeId[userId] = nodeId
        nodeIdToPeer[nodeId]?.let { peer ->
            nodeIdToPeer[nodeId] = peer.copy(userId = userId)
            publishPeersSnapshot()
        }
    }

    fun clearSession() {
        stopBleTransport()
        localUserId = -1
        localUsername = ""
        nodeIdToPeer.clear()
        addressToNodeId.clear()
        userIdToNodeId.clear()
        _peers.value = emptyList()
        _relayEvents.value = emptyList()
    }

    // -------------------------------------------------------------------------
    // 2. PERMISSIONS & HARDWARE STATE
    // -------------------------------------------------------------------------

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasRequiredBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshSystemBluetoothState() {
        val permsOk = hasRequiredBlePermissions()
        _hasBlePermissions.value = permsOk
        val adapter = bluetoothAdapter
        val btOn = adapter?.isEnabled == true
        _isBluetoothEnabled.value = btOn

        if (adapter == null) {
            _bleHardwareStatus.value = "No Bluetooth Adapter on this device"
            _isBleRadioActive.value = false
            return
        }
        if (!permsOk) {
            _bleHardwareStatus.value = "Bluetooth permissions required"
            _isBleRadioActive.value = false
            return
        }
        if (!btOn) {
            _bleHardwareStatus.value = "Bluetooth is OFF • Turn on to pair & connect"
            _isBleRadioActive.value = false
            return
        }

        // Load bonded devices from system Bluetooth
        loadBondedDevicesIntoList()
        if (!_isBleRadioActive.value) {
            _bleHardwareStatus.value = "Bluetooth Ready • ${_discoveredDevices.value.count { it.isBonded }} paired"
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedDevicesIntoList() {
        if (!hasConnectPermission()) return
        val adapter = bluetoothAdapter ?: return
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: emptySet()
        val currentMap = _discoveredDevices.value.associateBy { it.address }.toMutableMap()

        bonded.forEach { dev ->
            val addr = dev.address ?: return@forEach
            val existing = currentMap[addr]
            val devName = runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: existing?.name
                ?: addr
            val connected = rfcommLinks.containsKey(addr) || gattClientLinks.containsKey(addr)
            currentMap[addr] = DiscoveredBtDevice(
                address = addr,
                name = devName,
                rssi = existing?.rssi ?: -55,
                bondState = dev.bondState,
                isMeshCapable = existing?.isMeshCapable == true || devName.startsWith("CPI-"),
                isConnected = connected,
                isConnecting = existing?.isConnecting == true,
                transportType = existing?.transportType ?: "Classic + BLE",
                pairedNodeId = existing?.pairedNodeId ?: addressToNodeId[addr],
                pairedUsername = existing?.pairedUsername
            )
        }
        _discoveredDevices.value = sortDiscoveredDevices(currentMap.values)
    }

    private fun sortDiscoveredDevices(devices: Collection<DiscoveredBtDevice>): List<DiscoveredBtDevice> {
        return devices.sortedWith(
            compareByDescending<DiscoveredBtDevice> { it.isConnected }
                .thenByDescending { it.isMeshCapable }
                .thenByDescending { it.isBonded }
                .thenByDescending { it.rssi }
        )
    }

    // -------------------------------------------------------------------------
    // 3. CLASSIC BLUETOOTH DISCOVERY & PAIRING (BroadcastReceiver)
    // -------------------------------------------------------------------------

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val enabled = state == BluetoothAdapter.STATE_ON
                    _isBluetoothEnabled.value = enabled
                    if (enabled && localUsername.isNotBlank()) {
                        startBleTransportIfPermitted()
                    } else if (!enabled) {
                        stopBleTransport()
                        _bleHardwareStatus.value = "Bluetooth turned OFF"
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanningDevices.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (!isBleScanning) {
                        _isScanningDevices.value = false
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-70).toShort()).toInt()
                    val devName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address
                    upsertDiscoveredDevice(
                        address = device.address,
                        name = devName,
                        rssi = rssi,
                        bondState = device.bondState,
                        isMeshCapable = devName.startsWith("CPI-"),
                        transportType = "Classic BT"
                    )
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val devName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address
                    upsertDiscoveredDevice(
                        address = device.address,
                        name = devName,
                        rssi = -55,
                        bondState = bondState,
                        isMeshCapable = devName.startsWith("CPI-"),
                        transportType = "Classic + BLE"
                    )
                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            _pairingStatusMessage.value = "Pairing with $devName..."
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            _pairingStatusMessage.value = "Paired with $devName! Establishing mesh link..."
                            connectToDevice(device.address)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            _pairingStatusMessage.value = "Pairing cancelled or failed for $devName"
                        }
                    }
                }
            }
        }
    }

    private fun registerBluetoothReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(btReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(btReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun upsertDiscoveredDevice(
        address: String,
        name: String,
        rssi: Int,
        bondState: Int,
        isMeshCapable: Boolean,
        transportType: String
    ) {
        val list = _discoveredDevices.value.toMutableList()
        val idx = list.indexOfFirst { it.address == address }
        val connected = rfcommLinks.containsKey(address) || gattClientLinks.containsKey(address)
        val existing = if (idx >= 0) list[idx] else null
        val updated = DiscoveredBtDevice(
            address = address,
            name = if (name != address) name else (existing?.name ?: address),
            rssi = rssi,
            bondState = bondState,
            isMeshCapable = isMeshCapable || (existing?.isMeshCapable == true),
            isConnected = connected,
            isConnecting = existing?.isConnecting == true,
            transportType = if (existing != null && existing.transportType != transportType) "BLE + Classic" else transportType,
            pairedNodeId = existing?.pairedNodeId ?: addressToNodeId[address],
            pairedUsername = existing?.pairedUsername,
            lastSeenAt = System.currentTimeMillis()
        )
        if (idx >= 0) list[idx] = updated else list.add(updated)
        _discoveredDevices.value = sortDiscoveredDevices(list)
    }

    private fun markDeviceConnectionState(
        address: String,
        isConnected: Boolean,
        isConnecting: Boolean,
        nodeId: String? = null,
        username: String? = null
    ) {
        val list = _discoveredDevices.value.toMutableList()
        val idx = list.indexOfFirst { it.address == address }
        if (idx >= 0) {
            val cur = list[idx]
            list[idx] = cur.copy(
                isConnected = isConnected,
                isConnecting = isConnecting,
                pairedNodeId = nodeId ?: cur.pairedNodeId,
                pairedUsername = username ?: cur.pairedUsername
            )
            _discoveredDevices.value = sortDiscoveredDevices(list)
        }
    }

    // -------------------------------------------------------------------------
    // 4. BLE GATT SERVER/CLIENT + BLE ADVERTISING/SCANNING + RFCOMM SERVER
    // -------------------------------------------------------------------------

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val addr = device.address ?: return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gattServerSubscribedDevices[addr] = device
                updateHardwareStatusBanner()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gattServerSubscribedDevices.remove(addr)
                handleLinkDisconnected(addr)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            gattServerSubscribedDevices[device.address] = device
            if (responseNeeded && hasConnectPermission()) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded && hasConnectPermission()) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            if (value != null && characteristic.uuid == ECHO_MESH_CHAR_RX_UUID) {
                gattServerSubscribedDevices[device.address] = device
                onIncomingGattChunk(device.address, value)
            }
        }
    }

    private fun onIncomingGattChunk(fromAddress: String, chunk: ByteArray) {
        if (chunk.size < 8) return
        val bb = ByteBuffer.wrap(chunk)
        val msgId = bb.int
        val chunkIdx = bb.short.toInt() and 0xFFFF
        val totalChunks = bb.short.toInt() and 0xFFFF
        if (totalChunks <= 0 || chunkIdx >= totalChunks) return

        val payload = ByteArray(chunk.size - 8)
        bb.get(payload)

        val key = "$fromAddress#$msgId"
        val map = gattWireAssembly.getOrPut(key) { ConcurrentHashMap() }
        map[chunkIdx] = payload

        if (map.size >= totalChunks) {
            val out = ByteArrayOutputStream()
            for (i in 0 until totalChunks) {
                out.write(map[i] ?: ByteArray(0))
            }
            gattWireAssembly.remove(key)
            val jsonStr = String(out.toByteArray(), Charsets.UTF_8)
            scope.launch {
                handleIncomingWireJson(jsonStr, fromAddress, transport = "BLE GATT")
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val addr = dev.address ?: return
            val recordName = result.scanRecord?.deviceName
            val devName = if (hasConnectPermission()) {
                runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: recordName ?: addr
            } else {
                recordName ?: addr
            }
            val hasEchoUuid = result.scanRecord?.serviceUuids?.contains(ECHO_MESH_SERVICE_UUID) == true
            val serviceData = result.scanRecord?.getServiceData(ECHO_MESH_SERVICE_UUID)
            val advertisedHint = serviceData?.let { String(it, Charsets.UTF_8) }
            val meshCapable = hasEchoUuid || advertisedHint != null || devName.startsWith("CPI-")

            val bondState = if (hasConnectPermission()) dev.bondState else BluetoothDevice.BOND_NONE
            upsertDiscoveredDevice(
                address = addr,
                name = advertisedHint?.removePrefix("CPI:")?.ifBlank { devName } ?: devName,
                rssi = result.rssi,
                bondState = bondState,
                isMeshCapable = meshCapable,
                transportType = "BLE"
            )
            updateHardwareStatusBanner()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed code=$errorCode")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            _isBleRadioActive.value = true
            updateHardwareStatusBanner()
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            updateHardwareStatusBanner()
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleTransportIfPermitted() {
        refreshSystemBluetoothState()
        if (!hasRequiredBlePermissions()) {
            _bleHardwareStatus.value = "Grant Bluetooth permissions to activate Mesh"
            _isBleRadioActive.value = false
            return
        }
        val manager = bluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            _bleHardwareStatus.value = "No Bluetooth hardware adapter available"
            _isBleRadioActive.value = false
            return
        }
        if (!adapter.isEnabled) {
            _bleHardwareStatus.value = "Bluetooth is OFF • Enable Bluetooth to pair"
            _isBleRadioActive.value = false
            return
        }

        try {
            bleScanner = adapter.bluetoothLeScanner
            bleAdvertiser = adapter.bluetoothLeAdvertiser

            // 1. Open BLE GATT Server for multi-peer BLE transfers
            if (gattServer == null) {
                gattServer = manager.openGattServer(context, gattServerCallback)?.apply {
                    val meshService = BluetoothGattService(
                        ECHO_MESH_SERVICE_UUID.uuid,
                        BluetoothGattService.SERVICE_TYPE_PRIMARY
                    )
                    val rxChar = BluetoothGattCharacteristic(
                        ECHO_MESH_CHAR_RX_UUID,
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                    )
                    val txChar = BluetoothGattCharacteristic(
                        ECHO_MESH_CHAR_TX_UUID,
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                    ).apply {
                        addDescriptor(
                            BluetoothGattDescriptor(
                                CCCD_UUID,
                                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                            )
                        )
                    }
                    gattTxCharacteristic = txChar
                    meshService.addCharacteristic(rxChar)
                    meshService.addCharacteristic(txChar)
                    addService(meshService)
                }
            }

            // 2. Start BLE Advertising with CPI Social Service UUID & short node hint
            if (!isAdvertising && bleAdvertiser != null) {
                val advSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()
                val hintStr = "CPI:${localUsername.ifBlank { localNodeId }.take(12)}"
                val advData = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(ECHO_MESH_SERVICE_UUID)
                    .addServiceData(ECHO_MESH_SERVICE_UUID, hintStr.toByteArray(Charsets.UTF_8))
                    .build()
                bleAdvertiser?.startAdvertising(advSettings, advData, advertiseCallback)
            }

            // 3. Start Classic RFCOMM Mesh Listener for paired/nearby phones
            startRfcommServer()

            // 4. Start initial BLE scan for nearby CPI nodes
            startDiscoveryScan(includeClassicDiscovery = false)

            _isBleRadioActive.value = true
            updateHardwareStatusBanner()
        } catch (se: SecurityException) {
            _bleHardwareStatus.value = "Bluetooth Permission Denied"
            _isBleRadioActive.value = false
        } catch (e: Exception) {
            _bleHardwareStatus.value = "Bluetooth Init Error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRfcommServer() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission() || !adapter.isEnabled) return
        if (rfcommAcceptJob?.isActive == true) return

        rfcommAcceptJob = scope.launch {
            try {
                rfcommServerSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    "CPISocialMesh",
                    MESH_RFCOMM_UUID
                )
                Log.d(TAG, "RFCOMM Mesh server listening on $MESH_RFCOMM_UUID")
                while (isActive) {
                    val socket = try {
                        rfcommServerSocket?.accept()
                    } catch (_: Exception) {
                        break
                    } ?: break
                    setupRfcommLink(socket, sendHelloImmediately = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "RFCOMM server stopped: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscoveryScan(includeClassicDiscovery: Boolean = true) {
        refreshSystemBluetoothState()
        if (!hasRequiredBlePermissions() || bluetoothAdapter?.isEnabled != true) {
            _pairingStatusMessage.value = "Enable Bluetooth & grant permissions first."
            return
        }
        loadBondedDevicesIntoList()
        _isScanningDevices.value = true

        // BLE scan (unfiltered so both CPI nodes & nearby Bluetooth phones show up, prioritizing CPI UUID)
        runCatching {
            if (isBleScanning) bleScanner?.stopScan(bleScanCallback)
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner?.startScan(emptyList(), scanSettings, bleScanCallback)
            isBleScanning = true
        }

        // Classic Bluetooth Discovery (needed for pairing with unbonded devices via ACTION_FOUND)
        if (includeClassicDiscovery) {
            runCatching {
                val adapter = bluetoothAdapter
                if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
                adapter?.startDiscovery()
            }
        }

        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = scope.launch {
            delay(18000L)
            stopDiscoveryScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscoveryScan() {
        discoveryTimeoutJob?.cancel()
        if (isBleScanning && hasRequiredBlePermissions()) {
            runCatching { bleScanner?.stopScan(bleScanCallback) }
            isBleScanning = false
        }
        if (hasRequiredBlePermissions()) {
            runCatching {
                if (bluetoothAdapter?.isDiscovering == true) {
                    bluetoothAdapter?.cancelDiscovery()
                }
            }
        }
        _isScanningDevices.value = false
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(address: String) {
        if (!hasConnectPermission()) {
            _pairingStatusMessage.value = "Grant Bluetooth permissions to pair."
            return
        }
        val adapter = bluetoothAdapter ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        stopDiscoveryScan()

        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            _pairingStatusMessage.value = "Already paired. Connecting mesh..."
            connectToDevice(address)
            return
        }

        _pairingStatusMessage.value = "Requesting Bluetooth pairing with ${device.name ?: address}..."
        val initiated = runCatching { device.createBond() }.getOrDefault(false)
        if (!initiated) {
            // Even if bonding isn't supported on some BLE-only peripherals, try direct GATT/insecure RFCOMM connect
            _pairingStatusMessage.value = "Bonding unavailable, trying direct mesh link..."
            connectToDevice(address)
        }
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(address: String) {
        disconnectDevice(address)
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()
        if (device != null && hasConnectPermission()) {
            runCatching {
                val m = device.javaClass.getMethod("removeBond")
                m.invoke(device)
            }
        }
        refreshSystemBluetoothState()
        _pairingStatusMessage.value = "Unpaired $address"
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        if (!hasConnectPermission() || bluetoothAdapter?.isEnabled != true) {
            _pairingStatusMessage.value = "Enable Bluetooth first."
            return
        }
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull() ?: return
        stopDiscoveryScan()

        if (rfcommLinks.containsKey(address) || gattClientLinks.containsKey(address)) {
            sendHelloFrame(address)
            _pairingStatusMessage.value = "Mesh handshake refreshed with ${device.name ?: address}"
            return
        }

        markDeviceConnectionState(address, isConnected = false, isConnecting = true)
        _pairingStatusMessage.value = "Connecting to ${device.name ?: address}..."

        scope.launch {
            // First try Classic Bluetooth RFCOMM socket (fast, high-throughput for voice/images/mesh)
            val rfcommConnected = try {
                val sock = runCatching {
                    device.createInsecureRfcommSocketToServiceRecord(MESH_RFCOMM_UUID)
                }.getOrElse {
                    device.createRfcommSocketToServiceRecord(MESH_RFCOMM_UUID)
                }
                sock.connect()
                setupRfcommLink(sock, sendHelloImmediately = true)
                true
            } catch (e: Exception) {
                Log.w(TAG, "RFCOMM connect failed for $address (${e.message}), trying BLE GATT...")
                false
            }

            if (!rfcommConnected) {
                // Fallback to BLE GATT Client connection
                connectBleGattClient(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBleGattClient(device: BluetoothDevice) {
        val addr = device.address
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gattClientLinks[addr] = gatt
                    if (hasConnectPermission()) {
                        gatt.requestMtu(247)
                        gatt.discoverServices()
                    }
                    updateHardwareStatusBanner()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gattClientLinks.remove(addr)
                    gattClientRxChars.remove(addr)
                    runCatching { gatt.close() }
                    handleLinkDisconnected(addr)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    markDeviceConnectionState(addr, isConnected = false, isConnecting = false)
                    _pairingStatusMessage.value = "${device.name ?: addr} is not running CPI Social Mesh"
                    return
                }
                val service = gatt.getService(ECHO_MESH_SERVICE_UUID.uuid)
                val rxChar = service?.getCharacteristic(ECHO_MESH_CHAR_RX_UUID)
                val txChar = service?.getCharacteristic(ECHO_MESH_CHAR_TX_UUID)
                if (rxChar != null) {
                    gattClientRxChars[addr] = rxChar
                    if (txChar != null && hasConnectPermission()) {
                        gatt.setCharacteristicNotification(txChar, true)
                        txChar.getDescriptor(CCCD_UUID)?.let { desc ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(desc)
                            }
                        }
                    }
                    markDeviceConnectionState(addr, isConnected = true, isConnecting = false)
                    _pairingStatusMessage.value = "Connected over BLE GATT! Exchanging E2EE keys..."
                    scope.launch {
                        delay(250)
                        sendHelloFrame(addr)
                    }
                } else {
                    markDeviceConnectionState(addr, isConnected = false, isConnecting = false)
                    _pairingStatusMessage.value = "Peer does not expose CPI Social Mesh service"
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == ECHO_MESH_CHAR_TX_UUID) {
                    @Suppress("DEPRECATION")
                    val bytes = characteristic.value ?: return
                    onIncomingGattChunk(addr, bytes)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == ECHO_MESH_CHAR_TX_UUID) {
                    onIncomingGattChunk(addr, value)
                }
            }
        }

        runCatching {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }.onFailure {
            markDeviceConnectionState(addr, isConnected = false, isConnecting = false)
            _pairingStatusMessage.value = "Connection failed: ${it.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupRfcommLink(socket: BluetoothSocket, sendHelloImmediately: Boolean) {
        val addr = socket.remoteDevice.address
        rfcommLinks.remove(addr)?.let { old ->
            old.readerJob.cancel()
            runCatching { old.socket.close() }
        }

        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        val readerJob = scope.launch {
            try {
                while (isActive && socket.isConnected) {
                    val length = input.readInt()
                    if (length <= 0 || length > 2_000_000) break
                    val buf = ByteArray(length)
                    input.readFully(buf)
                    val jsonStr = String(buf, Charsets.UTF_8)
                    handleIncomingWireJson(jsonStr, addr, transport = "Bluetooth RFCOMM")
                }
            } catch (e: Exception) {
                Log.d(TAG, "RFCOMM reader ended for $addr: ${e.message}")
            } finally {
                rfcommLinks.remove(addr)
                runCatching { socket.close() }
                handleLinkDisconnected(addr)
            }
        }

        rfcommLinks[addr] = RfcommLink(addr, socket, output, readerJob)
        markDeviceConnectionState(addr, isConnected = true, isConnecting = false)
        updateHardwareStatusBanner()

        if (sendHelloImmediately) {
            scope.launch {
                sendHelloFrame(addr)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(address: String) {
        rfcommLinks.remove(address)?.let { link ->
            link.readerJob.cancel()
            runCatching { link.socket.close() }
        }
        gattClientLinks.remove(address)?.let { gatt ->
            runCatching {
                if (hasConnectPermission()) {
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }
        gattClientRxChars.remove(address)
        handleLinkDisconnected(address)
    }

    private fun handleLinkDisconnected(address: String) {
        markDeviceConnectionState(address, isConnected = false, isConnecting = false)
        val droppedNodeId = addressToNodeId[address]
        var changed = false
        nodeIdToPeer.entries.forEach { (nodeId, peer) ->
            if (peer.nextHopAddress == address || nodeId == droppedNodeId) {
                nodeIdToPeer[nodeId] = peer.copy(
                    hopDistance = -1,
                    nextHopAddress = null,
                    nextHopNodeId = null,
                    nextHopUsername = null,
                    routePath = emptyList()
                )
                changed = true
            }
        }
        if (changed) publishPeersSnapshot()
        updateHardwareStatusBanner()
    }

    @SuppressLint("MissingPermission")
    fun stopBleTransport() {
        stopDiscoveryScan()
        try {
            if (isAdvertising && hasRequiredBlePermissions()) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
            }
            if (hasRequiredBlePermissions()) {
                gattServer?.close()
            }
        } catch (_: Exception) {
        } finally {
            rfcommAcceptJob?.cancel()
            rfcommAcceptJob = null
            runCatching { rfcommServerSocket?.close() }
            rfcommServerSocket = null

            rfcommLinks.values.forEach {
                it.readerJob.cancel()
                runCatching { it.socket.close() }
            }
            rfcommLinks.clear()

            gattClientLinks.values.forEach { runCatching { it.close() } }
            gattClientLinks.clear()
            gattClientRxChars.clear()
            gattServerSubscribedDevices.clear()

            gattServer = null
            isAdvertising = false
            _isBleRadioActive.value = false
            updateHardwareStatusBanner()
        }
    }

    private fun updateHardwareStatusBanner() {
        val activeLinks = (rfcommLinks.keys + gattClientLinks.keys + gattServerSubscribedDevices.keys).distinct().size
        val reachablePeers = nodeIdToPeer.values.count { it.hopDistance > 0 }
        _bleHardwareStatus.value = when {
            !hasRequiredBlePermissions() -> "Grant Bluetooth permissions to activate Mesh"
            !_isBluetoothEnabled.value -> "Bluetooth is OFF • Turn on to pair"
            activeLinks > 0 -> "Bluetooth Mesh Active • $activeLinks linked ($reachablePeers peers)"
            isAdvertising -> "BLE + RFCOMM Ready • Waiting for paired/nearby nodes"
            else -> "Bluetooth Mesh Ready"
        }
    }

    // -------------------------------------------------------------------------
    // 5. WIRE PROTOCOL: HELLO HANDSHAKE, MULTI-HOP ROUTING & PACKET RELAY
    // -------------------------------------------------------------------------

    private fun sendHelloFrame(address: String, isAck: Boolean = false) {
        if (localUsername.isBlank()) return
        val neighborsArr = JSONArray()
        nodeIdToPeer.values
            .filter { it.hopDistance in 1..4 }
            .forEach { peer ->
                neighborsArr.put(
                    JSONObject().apply {
                        put("nodeId", peer.nodeId)
                        put("username", peer.username)
                        put("publicKeyB64", peer.publicKeyB64)
                        put("hopDistance", peer.hopDistance)
                        put("routePath", JSONArray(peer.routePath))
                    }
                )
            }

        val frame = JSONObject().apply {
            put("frameType", if (isAck) "HELLO_ACK" else "HELLO")
            put("nodeId", localNodeId)
            put("username", localUsername)
            put("bio", localBio)
            put("publicKeyB64", getPublicKeyB64())
            put("neighbors", neighborsArr)
        }.toString()

        sendWireJsonToAddress(address, frame)
    }

    private fun broadcastHelloToAllLinks() {
        val targets = (rfcommLinks.keys + gattClientLinks.keys).distinct()
        targets.forEach { sendHelloFrame(it, isAck = false) }
    }

    private fun sendWireJsonToAddress(address: String, jsonStr: String): Boolean {
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)

        // 1. Prefer RFCOMM stream if connected
        val rfcomm = rfcommLinks[address]
        if (rfcomm != null && rfcomm.socket.isConnected) {
            return try {
                synchronized(rfcomm.output) {
                    rfcomm.output.writeInt(bytes.size)
                    rfcomm.output.write(bytes)
                    rfcomm.output.flush()
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "RFCOMM write failed to $address: ${e.message}")
                disconnectDevice(address)
                false
            }
        }

        // 2. Fallback to BLE GATT Client write or GATT Server notification
        val gatt = gattClientLinks[address]
        val rxChar = gattClientRxChars[address]
        if (gatt != null && rxChar != null && hasConnectPermission()) {
            return sendChunkedOverGattClient(gatt, rxChar, bytes)
        }

        val serverDevice = gattServerSubscribedDevices[address]
        val txChar = gattTxCharacteristic
        if (gattServer != null && serverDevice != null && txChar != null && hasConnectPermission()) {
            return sendChunkedOverGattServer(serverDevice, txChar, bytes)
        }

        return false
    }

    @SuppressLint("MissingPermission")
    private fun sendChunkedOverGattClient(
        gatt: BluetoothGatt,
        rxChar: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Boolean {
        val msgId = UUID.randomUUID().hashCode()
        val chunks = chunkBytes(payload, GATT_FRAME_MTU)
        return try {
            chunks.forEachIndexed { idx, part ->
                val frame = ByteBuffer.allocate(8 + part.size)
                    .putInt(msgId)
                    .putShort(idx.toShort())
                    .putShort(chunks.size.toShort())
                    .put(part)
                    .array()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(rxChar, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    rxChar.value = frame
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(rxChar)
                }
                Thread.sleep(15)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "GATT client write failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendChunkedOverGattServer(
        device: BluetoothDevice,
        txChar: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Boolean {
        val server = gattServer ?: return false
        val msgId = UUID.randomUUID().hashCode()
        val chunks = chunkBytes(payload, GATT_FRAME_MTU)
        return try {
            chunks.forEachIndexed { idx, part ->
                val frame = ByteBuffer.allocate(8 + part.size)
                    .putInt(msgId)
                    .putShort(idx.toShort())
                    .putShort(chunks.size.toShort())
                    .put(part)
                    .array()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, txChar, false, frame)
                } else {
                    @Suppress("DEPRECATION")
                    txChar.value = frame
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, txChar, false)
                }
                Thread.sleep(15)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "GATT server notify failed: ${e.message}")
            false
        }
    }

    private suspend fun handleIncomingWireJson(jsonStr: String, fromAddress: String, transport: String) {
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return
        when (obj.optString("frameType")) {
            "HELLO", "HELLO_ACK" -> {
                val isAck = obj.optString("frameType") == "HELLO_ACK"
                val peerNodeId = obj.optString("nodeId")
                val peerUsername = obj.optString("username")
                val peerBio = obj.optString("bio")
                val peerPubKey = obj.optString("publicKeyB64")
                if (peerNodeId.isBlank() || peerUsername.isBlank() || peerNodeId == localNodeId) return

                addressToNodeId[fromAddress] = peerNodeId
                val existingUserId = nodeIdToPeer[peerNodeId]?.userId
                    ?: userIdToNodeId.entries.find { it.value == peerNodeId }?.key
                    ?: -abs(peerNodeId.hashCode()).coerceAtMost(-2)

                val directRoute = listOf(localUsername.ifBlank { localNodeId }, peerUsername)
                val directPeer = MeshPeer(
                    userId = existingUserId,
                    username = peerUsername,
                    nodeId = peerNodeId,
                    bluetoothAddress = fromAddress,
                    publicKeyB64 = peerPubKey,
                    keyFingerprint = cryptoService.getKeyFingerprint(peerPubKey),
                    hopDistance = 1,
                    rssi = -52,
                    nextHopNodeId = peerNodeId,
                    nextHopAddress = fromAddress,
                    nextHopUsername = peerUsername,
                    routePath = directRoute,
                    isHardwareBle = true,
                    transportLabel = transport
                )
                nodeIdToPeer[peerNodeId] = directPeer

                val bonded = isAddressBonded(fromAddress)
                markDeviceConnectionState(
                    address = fromAddress,
                    isConnected = true,
                    isConnecting = false,
                    nodeId = peerNodeId,
                    username = peerUsername
                )
                _pairingStatusMessage.value = "Bluetooth Mesh linked with @$peerUsername (1 Hop)"

                _discoveredPeerProfiles.emit(
                    DiscoveredPeerProfile(
                        nodeId = peerNodeId,
                        username = peerUsername,
                        bio = peerBio,
                        publicKeyB64 = peerPubKey,
                        bluetoothAddress = fromAddress,
                        isBonded = bonded,
                        hopDistance = 1,
                        rssi = -52,
                        nextHopNodeId = peerNodeId,
                        nextHopAddress = fromAddress,
                        nextHopUsername = peerUsername,
                        routePath = directRoute,
                        transportLabel = transport
                    )
                )

                // Learn multi-hop routes advertised by this direct peer!
                val neighbors = obj.optJSONArray("neighbors")
                if (neighbors != null) {
                    for (i in 0 until neighbors.length()) {
                        val nb = neighbors.optJSONObject(i) ?: continue
                        val nbNodeId = nb.optString("nodeId")
                        val nbUsername = nb.optString("username")
                        val nbPubKey = nb.optString("publicKeyB64")
                        val nbHops = nb.optInt("hopDistance", 1) + 1
                        if (nbNodeId.isBlank() || nbNodeId == localNodeId || nbHops > MAX_TTL) continue

                        val currentBest = nodeIdToPeer[nbNodeId]
                        if (currentBest == null || currentBest.hopDistance <= 0 || nbHops < currentBest.hopDistance) {
                            val remotePath = mutableListOf(localUsername.ifBlank { localNodeId })
                            val arr = nb.optJSONArray("routePath")
                            if (arr != null && arr.length() > 0) {
                                for (j in 0 until arr.length()) {
                                    val step = arr.optString(j)
                                    if (step.isNotBlank() && step != localUsername) remotePath.add(step)
                                }
                            } else {
                                remotePath.add(peerUsername)
                                remotePath.add(nbUsername)
                            }
                            val nbUserId = currentBest?.userId
                                ?: userIdToNodeId.entries.find { it.value == nbNodeId }?.key
                                ?: -abs(nbNodeId.hashCode()).coerceAtMost(-2)

                            nodeIdToPeer[nbNodeId] = MeshPeer(
                                userId = nbUserId,
                                username = nbUsername,
                                nodeId = nbNodeId,
                                bluetoothAddress = "",
                                publicKeyB64 = nbPubKey,
                                keyFingerprint = cryptoService.getKeyFingerprint(nbPubKey),
                                hopDistance = nbHops,
                                rssi = -72 - (nbHops * 6),
                                nextHopNodeId = peerNodeId,
                                nextHopAddress = fromAddress,
                                nextHopUsername = peerUsername,
                                routePath = remotePath,
                                isHardwareBle = true,
                                transportLabel = "$transport Relay"
                            )

                            _discoveredPeerProfiles.emit(
                                DiscoveredPeerProfile(
                                    nodeId = nbNodeId,
                                    username = nbUsername,
                                    bio = "Multi-hop Mesh Peer ($nbHops hops via @$peerUsername)",
                                    publicKeyB64 = nbPubKey,
                                    bluetoothAddress = "",
                                    isBonded = false,
                                    hopDistance = nbHops,
                                    rssi = -72 - (nbHops * 6),
                                    nextHopNodeId = peerNodeId,
                                    nextHopAddress = fromAddress,
                                    nextHopUsername = peerUsername,
                                    routePath = remotePath,
                                    transportLabel = "$transport Relay"
                                )
                            )
                        }
                    }
                }

                publishPeersSnapshot()
                updateHardwareStatusBanner()

                if (!isAck) {
                    sendHelloFrame(fromAddress, isAck = true)
                }
            }

            "PACKET" -> {
                val packet = deserializeMeshPacket(obj) ?: return
                processIncomingMeshPacket(packet, fromAddress)
            }

            "POST_SYNC" -> {
                val senderNodeId = obj.optString("senderNodeId")
                val senderUsername = obj.optString("senderUsername")
                val caption = obj.optString("caption")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val packetId = obj.optString("packetId")
                if (packetId.isNotBlank()) {
                    synchronized(seenPacketCache) {
                        if (seenPacketCache.containsKey(packetId)) return
                        seenPacketCache[packetId] = System.currentTimeMillis()
                    }
                }
                if (senderNodeId != localNodeId && caption.isNotBlank()) {
                    _incomingContent.emit(
                        IncomingMeshContent.SyncedPost(senderNodeId, senderUsername, caption, createdAt)
                    )
                    // Relay post sync to other neighbors if TTL allows
                    val ttl = obj.optInt("ttl", 3)
                    if (ttl > 1) {
                        val fwd = JSONObject(jsonStr).apply { put("ttl", ttl - 1) }.toString()
                        forwardToOtherConnectedLinks(fromAddress, fwd)
                    }
                }
            }

            "NOTE_SYNC" -> {
                val senderNodeId = obj.optString("senderNodeId")
                val senderUsername = obj.optString("senderUsername")
                val content = obj.optString("content")
                val musicTrack = obj.optString("musicTrack").takeIf { it.isNotBlank() }
                val packetId = obj.optString("packetId")
                if (packetId.isNotBlank()) {
                    synchronized(seenPacketCache) {
                        if (seenPacketCache.containsKey(packetId)) return
                        seenPacketCache[packetId] = System.currentTimeMillis()
                    }
                }
                if (senderNodeId != localNodeId && content.isNotBlank()) {
                    _incomingContent.emit(
                        IncomingMeshContent.SyncedNote(senderNodeId, senderUsername, content, musicTrack)
                    )
                    val ttl = obj.optInt("ttl", 3)
                    if (ttl > 1) {
                        val fwd = JSONObject(jsonStr).apply { put("ttl", ttl - 1) }.toString()
                        forwardToOtherConnectedLinks(fromAddress, fwd)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isAddressBonded(address: String): Boolean {
        if (!hasConnectPermission()) return false
        return runCatching {
            bluetoothAdapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        }.getOrDefault(false)
    }

    private fun forwardToOtherConnectedLinks(excludeAddress: String, jsonStr: String) {
        val targets = (rfcommLinks.keys + gattClientLinks.keys + gattServerSubscribedDevices.keys)
            .distinct()
            .filter { it != excludeAddress }
        targets.forEach { addr ->
            sendWireJsonToAddress(addr, jsonStr)
        }
    }

    private suspend fun processIncomingMeshPacket(packet: MeshPacket, fromAddress: String) {
        synchronized(seenPacketCache) {
            if (seenPacketCache.containsKey(packet.packetId)) return
            seenPacketCache[packet.packetId] = System.currentTimeMillis()
        }

        // Not for us -> act as Zero-Knowledge Mesh Relay!
        if (packet.recipientNodeId != localNodeId) {
            if (packet.ttl <= 1) return

            val nextHopCount = packet.hopCount + 1
            val nextTtl = packet.ttl - 1
            val canDecrypt = cryptoService.attemptRelaySnooping(
                envelope = packet.encryptedPayload,
                relayPrivateKey = localKeyPair.private,
                aadContext = packet.aadContext()
            )
            val targetName = nodeIdToPeer[packet.recipientNodeId]?.username ?: packet.recipientNodeId
            val event = RelayEvent(
                packetId = packet.packetId,
                packetType = packet.packetType,
                relayNodeName = localUsername.ifBlank { localNodeId },
                senderName = packet.senderUsername,
                targetUsername = targetName,
                hopNumber = nextHopCount,
                ttlRemaining = nextTtl,
                ciphertextSnippet = cryptoService.inspectCiphertextSnippet(packet.encryptedPayload),
                relayCanDecrypt = canDecrypt
            )
            _relayEvents.value = (listOf(event) + _relayEvents.value).take(40)

            val forwarded = packet.copy(
                ttl = nextTtl,
                hopCount = nextHopCount,
                relayPath = packet.relayPath + localUsername.ifBlank { localNodeId }
            )
            val wireJson = serializeMeshPacket(forwarded).toString()
            val targetNextAddr = nodeIdToPeer[packet.recipientNodeId]?.nextHopAddress
            if (targetNextAddr != null && targetNextAddr != fromAddress) {
                sendWireJsonToAddress(targetNextAddr, wireJson)
            } else {
                forwardToOtherConnectedLinks(fromAddress, wireJson)
            }
            return
        }

        // Packet is addressed to THIS device -> decrypt with local private key!
        val decryptedBytes = try {
            cryptoService.decryptFromSender(
                envelope = packet.encryptedPayload,
                recipientPrivateKey = localKeyPair.private,
                aadContext = packet.aadContext()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed for packet ${packet.packetId}", e)
            return
        }

        val finalHop = (packet.hopCount + 1).coerceAtLeast(1)
        val fullRoute = (packet.relayPath + localUsername.ifBlank { localNodeId }).joinToString(" → ")

        // Send encrypted Delivery ACK back to sender for non-streaming / final chunks
        val shouldAck = packet.packetType == MeshPacketType.TEXT ||
            packet.packetType == MeshPacketType.PTT_VOICE_BURST ||
            (packet.chunkIndex == packet.totalChunks - 1 &&
                (packet.packetType == MeshPacketType.IMAGE_CHUNK || packet.packetType == MeshPacketType.VOICE_NOTE_CHUNK))

        if (shouldAck) {
            sendEncryptedAckBack(
                originalPacketId = packet.transferId ?: packet.packetId,
                targetSenderNodeId = packet.senderNodeId,
                senderPublicKeyB64 = packet.encryptedPayload.senderPublicKeyB64,
                fallbackAddress = fromAddress
            )
        }

        when (packet.packetType) {
            MeshPacketType.ACK -> {
                val origId = String(decryptedBytes, Charsets.UTF_8)
                _incomingContent.emit(
                    IncomingMeshContent.DeliveryAck(
                        originalPacketId = origId,
                        fromNodeId = packet.senderNodeId,
                        hopCount = finalHop,
                        relayRoute = fullRoute
                    )
                )
            }

            MeshPacketType.TEXT -> {
                _incomingContent.emit(
                    IncomingMeshContent.TextMessage(
                        packetId = packet.packetId,
                        senderNodeId = packet.senderNodeId,
                        senderUsername = packet.senderUsername,
                        text = String(decryptedBytes, Charsets.UTF_8),
                        hopCount = finalHop,
                        relayRoute = fullRoute
                    )
                )
            }

            MeshPacketType.IMAGE_CHUNK, MeshPacketType.VOICE_NOTE_CHUNK -> {
                val tId = packet.transferId ?: packet.packetId
                val map = chunkAssemblyBuffer.getOrPut(tId) { ConcurrentHashMap() }
                map[packet.chunkIndex] = decryptedBytes
                if (packet.metadataJson.isNotEmpty()) {
                    chunkMetadataBuffer[tId] = packet.metadataJson
                }
                if (map.size >= packet.totalChunks) {
                    val reassembled = ByteArrayOutputStream()
                    for (i in 0 until packet.totalChunks) {
                        reassembled.write(map[i] ?: ByteArray(0))
                    }
                    val metaStr = chunkMetadataBuffer.remove(tId) ?: "{}"
                    chunkAssemblyBuffer.remove(tId)
                    val meta = runCatching { JSONObject(metaStr) }.getOrNull()

                    if (packet.packetType == MeshPacketType.IMAGE_CHUNK) {
                        _incomingContent.emit(
                            IncomingMeshContent.ImageMessage(
                                transferId = tId,
                                senderNodeId = packet.senderNodeId,
                                senderUsername = packet.senderUsername,
                                imageBytes = reassembled.toByteArray(),
                                hopCount = finalHop,
                                totalChunks = packet.totalChunks,
                                compressedSize = reassembled.size(),
                                originalSize = meta?.optInt("originalBytes", reassembled.size()) ?: reassembled.size(),
                                relayRoute = fullRoute
                            )
                        )
                    } else {
                        val durationSec = meta?.optInt("durationSec", 3) ?: 3
                        val wf = meta?.optString("waveform", "")
                            ?.split(",")
                            ?.mapNotNull { it.toFloatOrNull() }
                            ?.takeIf { it.isNotEmpty() }
                            ?: listOf(0.3f, 0.7f, 0.5f, 0.9f, 0.4f)
                        _incomingContent.emit(
                            IncomingMeshContent.VoiceNoteMessage(
                                transferId = tId,
                                senderNodeId = packet.senderNodeId,
                                senderUsername = packet.senderUsername,
                                audioBytes = reassembled.toByteArray(),
                                durationSec = durationSec,
                                waveform = wf,
                                hopCount = finalHop,
                                totalChunks = packet.totalChunks,
                                relayRoute = fullRoute
                            )
                        )
                    }
                }
            }

            MeshPacketType.CALL_SIGNAL -> {
                val sigJson = runCatching { JSONObject(String(decryptedBytes, Charsets.UTF_8)) }.getOrNull()
                if (sigJson != null) {
                    _incomingContent.emit(
                        IncomingMeshContent.CallSignal(
                            senderNodeId = packet.senderNodeId,
                            senderUsername = packet.senderUsername,
                            action = sigJson.optString("action"),
                            callType = sigJson.optString("callType", "voice"),
                            hopCount = finalHop,
                            relayRoute = fullRoute
                        )
                    )
                }
            }

            MeshPacketType.CALL_AUDIO_FRAME -> {
                val seq = runCatching {
                    JSONObject(packet.metadataJson).optInt("seq", 0)
                }.getOrDefault(0)
                _incomingContent.emit(
                    IncomingMeshContent.DirectAudioFrame(
                        senderNodeId = packet.senderNodeId,
                        pcmBytes = decryptedBytes,
                        sequence = seq
                    )
                )
            }

            MeshPacketType.PTT_VOICE_BURST -> {
                val meta = runCatching { JSONObject(packet.metadataJson) }.getOrNull()
                _incomingContent.emit(
                    IncomingMeshContent.PttVoiceBurst(
                        senderNodeId = packet.senderNodeId,
                        audioBytes = decryptedBytes,
                        durationMs = meta?.optInt("durationMs", 1200) ?: 1200,
                        hopCount = finalHop,
                        relayRoute = fullRoute
                    )
                )
            }
        }
    }

    private fun sendEncryptedAckBack(
        originalPacketId: String,
        targetSenderNodeId: String,
        senderPublicKeyB64: String,
        fallbackAddress: String
    ) {
        val ackId = "ack_${UUID.randomUUID().toString().take(8)}"
        val aad = "$ackId|$localNodeId|$targetSenderNodeId|${MeshPacketType.ACK.name}|0/1"
        val envelope = cryptoService.encryptTextForRecipient(
            text = originalPacketId,
            senderPrivateKey = localKeyPair.private,
            senderPublicKey = localKeyPair.public,
            recipientPublicKeyB64 = senderPublicKeyB64,
            aadContext = aad
        )
        val ackPacket = MeshPacket(
            packetId = ackId,
            senderNodeId = localNodeId,
            senderUsername = localUsername.ifBlank { localNodeId },
            recipientNodeId = targetSenderNodeId,
            packetType = MeshPacketType.ACK,
            relayPath = listOf(localUsername.ifBlank { localNodeId }),
            encryptedPayload = envelope
        )
        val wireJson = serializeMeshPacket(ackPacket).toString()
        val addr = nodeIdToPeer[targetSenderNodeId]?.nextHopAddress ?: fallbackAddress
        sendWireJsonToAddress(addr, wireJson)
    }

    private fun dispatchPacketOverBluetooth(packet: MeshPacket, targetPeer: MeshPeer): Boolean {
        synchronized(seenPacketCache) {
            seenPacketCache[packet.packetId] = System.currentTimeMillis()
        }
        val wireJson = serializeMeshPacket(packet).toString()
        val nextAddr = targetPeer.nextHopAddress
        return if (!nextAddr.isNullOrBlank()) {
            sendWireJsonToAddress(nextAddr, wireJson)
        } else {
            val allLinks = (rfcommLinks.keys + gattClientLinks.keys + gattServerSubscribedDevices.keys).distinct()
            var anySent = false
            allLinks.forEach { addr ->
                if (sendWireJsonToAddress(addr, wireJson)) anySent = true
            }
            anySent
        }
    }

    // -------------------------------------------------------------------------
    // 6. PUBLIC MESSAGING, MEDIA, CALL & SOCIAL SYNC APIS
    // -------------------------------------------------------------------------

    fun getSafetyNumberWith(userId: Int): String {
        val peer = getPeer(userId) ?: return "Not Paired"
        val myPub = cryptoService.encodePublicKey(localKeyPair.public)
        return cryptoService.computeSafetyNumber(myPub, peer.publicKeyB64)
    }

    fun getPeer(userId: Int): MeshPeer? {
        val nodeId = userIdToNodeId[userId]
        return if (nodeId != null) {
            nodeIdToPeer[nodeId]
        } else {
            nodeIdToPeer.values.find { it.userId == userId }
        }
    }

    fun getPeerByNodeId(nodeId: String): MeshPeer? = nodeIdToPeer[nodeId]

    fun getNearestReachablePeer(): MeshPeer? =
        nodeIdToPeer.values.filter { it.hopDistance > 0 }.minByOrNull { it.hopDistance }

    fun sendEncryptedText(
        recipientId: Int,
        text: String,
        onDispatched: (packetId: String, hopDistance: Int, routeLabel: String, sentOverBluetooth: Boolean) -> Unit
    ) {
        val peer = getPeer(recipientId) ?: run {
            onDispatched(UUID.randomUUID().toString().take(12), -1, "Peer Not Connected", false)
            return
        }
        val packetId = UUID.randomUUID().toString().take(12)
        val packetType = MeshPacketType.TEXT
        val aad = "$packetId|$localNodeId|${peer.nodeId}|${packetType.name}|0/1"

        val envelope = cryptoService.encryptTextForRecipient(
            text = text,
            senderPrivateKey = localKeyPair.private,
            senderPublicKey = localKeyPair.public,
            recipientPublicKeyB64 = peer.publicKeyB64,
            aadContext = aad
        )

        val packet = MeshPacket(
            packetId = packetId,
            senderNodeId = localNodeId,
            senderUsername = localUsername.ifBlank { localNodeId },
            recipientNodeId = peer.nodeId,
            packetType = packetType,
            relayPath = listOf(localUsername.ifBlank { localNodeId }),
            encryptedPayload = envelope
        )

        val routeLabel = if (peer.routePath.isNotEmpty()) {
            peer.routePath.joinToString(" → ")
        } else {
            "$localUsername → Offline"
        }

        scope.launch {
            val sent = if (peer.hopDistance > 0) dispatchPacketOverBluetooth(packet, peer) else false
            onDispatched(packetId, peer.hopDistance.coerceAtLeast(1), routeLabel, sent)
        }
    }

    fun sendEncryptedImage(
        recipientId: Int,
        imageUri: Uri,
        onPrepared: (transferId: String, localSavedPath: String, hopDistance: Int, routeLabel: String, summary: String, sentOverBt: Boolean) -> Unit
    ) {
        val peer = getPeer(recipientId) ?: return
        scope.launch {
            val compressed = compressImageFromUri(imageUri) ?: return@launch
            val (compressedBytes, originalBytes, savedPath) = compressed
            val transferId = "img_${UUID.randomUUID().toString().take(8)}"
            val chunks = chunkBytes(compressedBytes, BLE_CHUNK_BYTES)
            val routeLabel = peer.routePath.joinToString(" → ").ifEmpty { "$localUsername → ${peer.username}" }
            val summary = "${originalBytes / 1024} KB → ${compressedBytes.size / 1024} KB • ${chunks.size} BT chunks"

            val meta = JSONObject().apply {
                put("originalBytes", originalBytes)
                put("compressedBytes", compressedBytes.size)
            }.toString()

            var allSent = peer.hopDistance > 0
            onPrepared(transferId, savedPath, peer.hopDistance.coerceAtLeast(1), routeLabel, summary, allSent)

            if (!allSent) return@launch

            chunks.forEachIndexed { index, chunkBytes ->
                val packetId = "${transferId}_$index"
                val aad = "$packetId|$localNodeId|${peer.nodeId}|${MeshPacketType.IMAGE_CHUNK.name}|$index/${chunks.size}"
                val envelope = cryptoService.encryptForRecipient(
                    plaintext = chunkBytes,
                    senderPrivateKey = localKeyPair.private,
                    senderPublicKey = localKeyPair.public,
                    recipientPublicKeyB64 = peer.publicKeyB64,
                    aadContext = aad
                )
                val packet = MeshPacket(
                    packetId = packetId,
                    senderNodeId = localNodeId,
                    senderUsername = localUsername.ifBlank { localNodeId },
                    recipientNodeId = peer.nodeId,
                    packetType = MeshPacketType.IMAGE_CHUNK,
                    transferId = transferId,
                    chunkIndex = index,
                    totalChunks = chunks.size,
                    metadataJson = if (index == 0) meta else "",
                    relayPath = listOf(localUsername.ifBlank { localNodeId }),
                    encryptedPayload = envelope
                )

                _activeTransfer.value = MeshTransferProgress(
                    transferId = transferId,
                    recipientId = recipientId,
                    packetType = MeshPacketType.IMAGE_CHUNK,
                    sentChunks = index + 1,
                    totalChunks = chunks.size,
                    hopDistance = peer.hopDistance.coerceAtLeast(1),
                    compressedBytes = compressedBytes.size,
                    originalBytes = originalBytes
                )
                if (!dispatchPacketOverBluetooth(packet, peer)) {
                    allSent = false
                }
                delay(15L)
            }
            _activeTransfer.value = null
        }
    }

    fun sendEncryptedVoiceNote(
        recipientId: Int,
        audioBytes: ByteArray,
        durationSec: Int,
        waveform: List<Float>,
        onPrepared: (transferId: String, hopDistance: Int, routeLabel: String, sentOverBt: Boolean) -> Unit
    ) {
        val peer = getPeer(recipientId) ?: return
        val transferId = "aud_${UUID.randomUUID().toString().take(8)}"
        val chunks = chunkBytes(audioBytes, BLE_CHUNK_BYTES)
        val routeLabel = peer.routePath.joinToString(" → ").ifEmpty { "$localUsername → ${peer.username}" }
        val reachable = peer.hopDistance > 0

        onPrepared(transferId, peer.hopDistance.coerceAtLeast(1), routeLabel, reachable)
        if (!reachable) return

        val meta = JSONObject().apply {
            put("durationSec", durationSec)
            put("waveform", waveform.joinToString(",") { String.format("%.2f", it) })
        }.toString()

        scope.launch {
            chunks.forEachIndexed { index, chunkBytes ->
                val packetId = "${transferId}_$index"
                val aad = "$packetId|$localNodeId|${peer.nodeId}|${MeshPacketType.VOICE_NOTE_CHUNK.name}|$index/${chunks.size}"
                val envelope = cryptoService.encryptForRecipient(
                    plaintext = chunkBytes,
                    senderPrivateKey = localKeyPair.private,
                    senderPublicKey = localKeyPair.public,
                    recipientPublicKeyB64 = peer.publicKeyB64,
                    aadContext = aad
                )
                val packet = MeshPacket(
                    packetId = packetId,
                    senderNodeId = localNodeId,
                    senderUsername = localUsername.ifBlank { localNodeId },
                    recipientNodeId = peer.nodeId,
                    packetType = MeshPacketType.VOICE_NOTE_CHUNK,
                    transferId = transferId,
                    chunkIndex = index,
                    totalChunks = chunks.size,
                    metadataJson = if (index == 0) meta else "",
                    relayPath = listOf(localUsername.ifBlank { localNodeId }),
                    encryptedPayload = envelope
                )
                _activeTransfer.value = MeshTransferProgress(
                    transferId = transferId,
                    recipientId = recipientId,
                    packetType = MeshPacketType.VOICE_NOTE_CHUNK,
                    sentChunks = index + 1,
                    totalChunks = chunks.size,
                    hopDistance = peer.hopDistance.coerceAtLeast(1),
                    compressedBytes = audioBytes.size,
                    originalBytes = audioBytes.size
                )
                dispatchPacketOverBluetooth(packet, peer)
                delay(15L)
            }
            _activeTransfer.value = null
        }
    }

    fun sendDirectCallAudioFrame(recipientId: Int, pcmBytes: ByteArray, sequence: Int) {
        val peer = getPeer(recipientId) ?: return
        if (peer.hopDistance != 1) return
        val packetId = "frm_${sequence}_${UUID.randomUUID().toString().take(4)}"
        val aad = "$packetId|$localNodeId|${peer.nodeId}|${MeshPacketType.CALL_AUDIO_FRAME.name}|0/1"
        val envelope = cryptoService.encryptForRecipient(
            plaintext = pcmBytes,
            senderPrivateKey = localKeyPair.private,
            senderPublicKey = localKeyPair.public,
            recipientPublicKeyB64 = peer.publicKeyB64,
            aadContext = aad
        )
        val packet = MeshPacket(
            packetId = packetId,
            senderNodeId = localNodeId,
            senderUsername = localUsername.ifBlank { localNodeId },
            recipientNodeId = peer.nodeId,
            ttl = 1,
            maxTtl = 1,
            packetType = MeshPacketType.CALL_AUDIO_FRAME,
            metadataJson = JSONObject().apply { put("seq", sequence) }.toString(),
            relayPath = listOf(localUsername.ifBlank { localNodeId }),
            encryptedPayload = envelope
        )
        scope.launch {
            dispatchPacketOverBluetooth(packet, peer)
        }
    }

    fun sendPttVoiceBurst(recipientId: Int, audioBytes: ByteArray, durationMs: Int): Boolean {
        val peer = getPeer(recipientId) ?: return false
        if (peer.hopDistance <= 0) return false
        val packetId = "ptt_${UUID.randomUUID().toString().take(8)}"
        val aad = "$packetId|$localNodeId|${peer.nodeId}|${MeshPacketType.PTT_VOICE_BURST.name}|0/1"
        val envelope = cryptoService.encryptForRecipient(
            plaintext = audioBytes,
            senderPrivateKey = localKeyPair.private,
            senderPublicKey = localKeyPair.public,
            recipientPublicKeyB64 = peer.publicKeyB64,
            aadContext = aad
        )
        val meta = JSONObject().apply { put("durationMs", durationMs) }.toString()
        val packet = MeshPacket(
            packetId = packetId,
            senderNodeId = localNodeId,
            senderUsername = localUsername.ifBlank { localNodeId },
            recipientNodeId = peer.nodeId,
            packetType = MeshPacketType.PTT_VOICE_BURST,
            metadataJson = meta,
            relayPath = listOf(localUsername.ifBlank { localNodeId }),
            encryptedPayload = envelope
        )
        scope.launch {
            dispatchPacketOverBluetooth(packet, peer)
        }
        return true
    }

    fun sendCallSignal(recipientId: Int, action: String, callType: String): Boolean {
        val peer = getPeer(recipientId) ?: return false
        if (peer.hopDistance <= 0) return false
        val packetId = "sig_${UUID.randomUUID().toString().take(6)}"
        val aad = "$packetId|$localNodeId|${peer.nodeId}|${MeshPacketType.CALL_SIGNAL.name}|0/1"
        val payload = JSONObject().apply {
            put("action", action)
            put("callType", callType)
        }.toString()
        val envelope = cryptoService.encryptTextForRecipient(
            text = payload,
            senderPrivateKey = localKeyPair.private,
            senderPublicKey = localKeyPair.public,
            recipientPublicKeyB64 = peer.publicKeyB64,
            aadContext = aad
        )
        val packet = MeshPacket(
            packetId = packetId,
            senderNodeId = localNodeId,
            senderUsername = localUsername.ifBlank { localNodeId },
            recipientNodeId = peer.nodeId,
            packetType = MeshPacketType.CALL_SIGNAL,
            relayPath = listOf(localUsername.ifBlank { localNodeId }),
            encryptedPayload = envelope
        )
        scope.launch {
            dispatchPacketOverBluetooth(packet, peer)
        }
        return true
    }

    fun broadcastSocialPost(caption: String) {
        if (caption.isBlank() || localUsername.isBlank()) return
        val pktId = "post_${UUID.randomUUID().toString().take(8)}"
        synchronized(seenPacketCache) { seenPacketCache[pktId] = System.currentTimeMillis() }
        val json = JSONObject().apply {
            put("frameType", "POST_SYNC")
            put("packetId", pktId)
            put("senderNodeId", localNodeId)
            put("senderUsername", localUsername)
            put("caption", caption)
            put("createdAt", System.currentTimeMillis())
            put("ttl", 4)
        }.toString()
        scope.launch {
            forwardToOtherConnectedLinks("", json)
        }
    }

    fun broadcastUserNote(content: String, musicTrack: String?) {
        if (content.isBlank() || localUsername.isBlank()) return
        val pktId = "note_${UUID.randomUUID().toString().take(8)}"
        synchronized(seenPacketCache) { seenPacketCache[pktId] = System.currentTimeMillis() }
        val json = JSONObject().apply {
            put("frameType", "NOTE_SYNC")
            put("packetId", pktId)
            put("senderNodeId", localNodeId)
            put("senderUsername", localUsername)
            put("content", content)
            put("musicTrack", musicTrack ?: "")
            put("ttl", 4)
        }.toString()
        scope.launch {
            forwardToOtherConnectedLinks("", json)
        }
    }

    private fun publishPeersSnapshot() {
        _peers.value = nodeIdToPeer.values.sortedWith(
            compareBy<MeshPeer> { if (it.hopDistance <= 0) 99 else it.hopDistance }
                .thenBy { it.username }
        )
    }

    // -------------------------------------------------------------------------
    // 7. JSON SERIALIZATION & MEDIA HELPERS
    // -------------------------------------------------------------------------

    private fun serializeMeshPacket(packet: MeshPacket): JSONObject {
        return JSONObject().apply {
            put("frameType", "PACKET")
            put("packetId", packet.packetId)
            put("senderNodeId", packet.senderNodeId)
            put("senderUsername", packet.senderUsername)
            put("recipientNodeId", packet.recipientNodeId)
            put("ttl", packet.ttl)
            put("maxTtl", packet.maxTtl)
            put("hopCount", packet.hopCount)
            put("packetType", packet.packetType.name)
            put("transferId", packet.transferId ?: "")
            put("chunkIndex", packet.chunkIndex)
            put("totalChunks", packet.totalChunks)
            put("metadataJson", packet.metadataJson)
            put("relayPath", JSONArray(packet.relayPath))
            put("timestamp", packet.timestamp)
            put("envelope", JSONObject().apply {
                put("ciphertextB64", packet.encryptedPayload.ciphertextB64)
                put("ivB64", packet.encryptedPayload.ivB64)
                put("senderPublicKeyB64", packet.encryptedPayload.senderPublicKeyB64)
                put("recipientKeyFingerprint", packet.encryptedPayload.recipientKeyFingerprint)
                put("algorithm", packet.encryptedPayload.algorithm)
            })
        }
    }

    private fun deserializeMeshPacket(obj: JSONObject): MeshPacket? {
        return runCatching {
            val envObj = obj.getJSONObject("envelope")
            val envelope = CryptoService.EncryptedEnvelope(
                ciphertextB64 = envObj.getString("ciphertextB64"),
                ivB64 = envObj.getString("ivB64"),
                senderPublicKeyB64 = envObj.getString("senderPublicKeyB64"),
                recipientKeyFingerprint = envObj.getString("recipientKeyFingerprint"),
                algorithm = envObj.optString("algorithm", "ECDH-P256 / AES-256-GCM")
            )
            val relayArr = obj.optJSONArray("relayPath")
            val relayPath = mutableListOf<String>()
            if (relayArr != null) {
                for (i in 0 until relayArr.length()) {
                    relayPath.add(relayArr.optString(i))
                }
            }
            MeshPacket(
                packetId = obj.getString("packetId"),
                senderNodeId = obj.getString("senderNodeId"),
                senderUsername = obj.getString("senderUsername"),
                recipientNodeId = obj.getString("recipientNodeId"),
                ttl = obj.optInt("ttl", MAX_TTL),
                maxTtl = obj.optInt("maxTtl", MAX_TTL),
                hopCount = obj.optInt("hopCount", 0),
                packetType = MeshPacketType.valueOf(obj.getString("packetType")),
                transferId = obj.optString("transferId").takeIf { it.isNotBlank() },
                chunkIndex = obj.optInt("chunkIndex", 0),
                totalChunks = obj.optInt("totalChunks", 1),
                metadataJson = obj.optString("metadataJson", ""),
                relayPath = relayPath,
                encryptedPayload = envelope,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun compressImageFromUri(uri: Uri): Triple<ByteArray, Int, String>? {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val originalSize = rawBytes.size
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

            val maxDim = 720
            val scale = minOf(
                maxDim.toFloat() / decoded.width.coerceAtLeast(1),
                maxDim.toFloat() / decoded.height.coerceAtLeast(1),
                1f
            )
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }
            scaled.compress(format, 65, out)
            val compressedBytes = out.toByteArray()

            val ext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
            val file = java.io.File(context.cacheDir, "mesh_img_${System.currentTimeMillis()}.$ext")
            file.writeBytes(compressedBytes)

            if (scaled != decoded) decoded.recycle()
            Triple(compressedBytes, originalSize, file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed", e)
            null
        }
    }

    private fun chunkBytes(data: ByteArray, chunkSize: Int): List<ByteArray> {
        if (data.isEmpty()) return listOf(ByteArray(0))
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            chunks.add(data.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    fun release() {
        stopBleTransport()
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(btReceiver) }
            isReceiverRegistered = false
        }
    }
}
