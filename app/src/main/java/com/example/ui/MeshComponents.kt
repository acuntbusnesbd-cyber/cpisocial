package com.example.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.Message
import com.example.data.User
import com.example.mesh.MeshCallTransportMode
import com.example.mesh.MeshPeer
import java.io.File

val MeshDirectGreen = Color(0xFF10B981)
val MeshRelayAmber = Color(0xFFF59E0B)
val MeshOfflineGray = Color(0xFF64748B)
val E2eeViolet = Color(0xFF8B5CF6)

@Composable
fun MeshHopBadge(
    hopDistance: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, label) = when {
        hopDistance == 1 -> Triple(
            MeshDirectGreen.copy(alpha = 0.18f),
            MeshDirectGreen,
            if (compact) "1 Hop" else "Direct • 1 Hop"
        )
        hopDistance >= 2 -> Triple(
            MeshRelayAmber.copy(alpha = 0.18f),
            MeshRelayAmber,
            if (compact) "$hopDistance Hops" else "Mesh • $hopDistance Hops"
        )
        else -> Triple(
            MeshOfflineGray.copy(alpha = 0.18f),
            MeshOfflineGray,
            "Offline"
        )
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hopDistance == 1) Icons.Rounded.BluetoothConnected else if (hopDistance >= 2) Icons.Rounded.Hub else Icons.Rounded.BluetoothDisabled,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(if (compact) 10.dp else 12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = fgColor,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Dialog triggered from the Top-Right Phone Icon so users can place an E2EE Mesh Voice Call
 * (1-Hop Direct Streaming or Multi-Hop PTT Walkie-Talkie) to connected Bluetooth peers.
 */
@Composable
fun MeshPeerCallPickerDialog(
    viewModel: EchoViewModel,
    onDismiss: () -> Unit,
    onOpenBluetoothPairing: (() -> Unit)? = null
) {
    val peers by viewModel.meshPeers.collectAsState()
    val users by viewModel.allUsers.collectAsState()
    val reachablePeers = peers.filter { it.hopDistance > 0 }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = "Mesh Call",
                            tint = MeshDirectGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bluetooth Mesh Call",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                Text(
                    text = "1-Hop paired peers stream live voice. 2+ Hop peers use Walkie-Talkie PTT.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (reachablePeers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BluetoothSearching,
                                contentDescription = "No Peers Connected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "No Bluetooth Mesh Peers Connected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Pair & connect with another phone running CPI Social to place encrypted offline voice calls.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (onOpenBluetoothPairing != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenBluetoothPairing()
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pair Bluetooth Device")
                                }
                            }
                        }
                    }
                } else {
                    reachablePeers.forEach { peer ->
                        val user = users.find { it.id == peer.userId || it.nodeId == peer.nodeId }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val targetId = user?.id ?: peer.userId
                                    if (targetId > 0) {
                                        viewModel.initiateCall(targetId)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!user?.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = user!!.avatarUrl,
                                            contentDescription = peer.username,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = peer.username.take(2).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "@${peer.username}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MeshHopBadge(hopDistance = peer.hopDistance, compact = true)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (peer.hopDistance == 1) "Live Voice" else "PTT Walkie-Talkie",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        val targetId = user?.id ?: peer.userId
                                        if (targetId > 0) {
                                            viewModel.initiateCall(targetId)
                                            onDismiss()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (peer.hopDistance == 1) Icons.Rounded.Call else Icons.Rounded.RecordVoiceOver,
                                        contentDescription = "Start Call",
                                        tint = if (peer.hopDistance == 1) MeshDirectGreen else MeshRelayAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real Bluetooth Pairing, Device Discovery & Multi-Hop Mesh Routing Hub.
 */
@Composable
fun MeshNetworkInspectorDialog(
    viewModel: EchoViewModel,
    onDismiss: () -> Unit
) {
    val peers by viewModel.meshPeers.collectAsState()
    val discoveredDevices by viewModel.discoveredBtDevices.collectAsState()
    val isScanning by viewModel.isScanningDevices.collectAsState()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val hasPerms by viewModel.hasBlePermissions.collectAsState()
    val pairingStatus by viewModel.pairingStatusMessage.collectAsState()
    val relayEvents by viewModel.relayEvents.collectAsState()
    val bleStatus by viewModel.bleHardwareStatus.collectAsState()
    val localFingerprint by viewModel.localKeyFingerprint.collectAsState()
    val localNodeId by viewModel.localNodeId.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshBlePermissionsAndRadio()
    }

    val btEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBlePermissionsAndRadio()
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBlePermissionsAndRadio()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothConnected,
                            contentDescription = "Bluetooth Pairing & Mesh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bluetooth Pairing & Mesh",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Local Node Card + Hardware Controls
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "@${currentUser?.username ?: "local_node"}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Node ID: $localNodeId",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = E2eeViolet.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "ECDH #$localFingerprint",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = E2eeViolet,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = bleStatus,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!pairingStatus.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = pairingStatus!!,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MeshDirectGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!hasPerms) {
                                        Button(
                                            onClick = { permissionLauncher.launch(requiredPermissions) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Grant BT + Mic", fontSize = 11.sp)
                                        }
                                    } else if (!isBtEnabled) {
                                        Button(
                                            onClick = {
                                                btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Turn On Bluetooth", fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (isScanning) viewModel.stopBluetoothDiscovery()
                                                else viewModel.startBluetoothDiscovery()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("bt_scan_toggle_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isScanning) Icons.Rounded.Stop else Icons.Rounded.BluetoothSearching,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isScanning) "Stop Scan" else "Scan Devices", fontSize = 11.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val discIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                                                }
                                                discoverableLauncher.launch(discIntent)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Discoverable", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Paired & Nearby Bluetooth Devices Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby & Paired Bluetooth Devices",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    if (discoveredDevices.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tap 'Scan Devices' to find nearby phones. Make sure the other phone also has CPI Social open or Bluetooth Discoverable enabled.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(discoveredDevices, key = { it.address }) { dev ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            dev.isConnected -> Icons.Rounded.BluetoothConnected
                                            dev.isBonded -> Icons.Rounded.Bluetooth
                                            else -> Icons.Rounded.BluetoothSearching
                                        },
                                        contentDescription = dev.name,
                                        tint = when {
                                            dev.isConnected -> MeshDirectGreen
                                            dev.isMeshCapable -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dev.pairedUsername?.let { "@$it (${dev.name})" } ?: dev.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = buildString {
                                                append(dev.address)
                                                append(" • ${dev.rssi} dBm")
                                                if (dev.isMeshCapable) append(" • CPI Mesh")
                                            },
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = when {
                                                dev.isConnected -> "Connected (${dev.transportType})"
                                                dev.isConnecting -> "Connecting..."
                                                dev.isBonding -> "Pairing..."
                                                dev.isBonded -> "Paired (Ready to Connect)"
                                                else -> "Not Paired"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when {
                                                dev.isConnected -> MeshDirectGreen
                                                dev.isBonded -> MaterialTheme.colorScheme.primary
                                                else -> MeshRelayAmber
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (dev.isConnected) {
                                        OutlinedButton(
                                            onClick = { viewModel.disconnectBluetoothDevice(dev.address) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Disconnect", fontSize = 11.sp)
                                        }
                                    } else if (dev.isBonded) {
                                        Button(
                                            onClick = { viewModel.connectBluetoothDevice(dev.address) },
                                            enabled = !dev.isConnecting,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (dev.isConnecting) "..." else "Connect", fontSize = 11.sp)
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = { viewModel.pairBluetoothDevice(dev.address) },
                                                enabled = !dev.isBonding && !dev.isConnecting,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (dev.isBonding) "Pairing" else "Pair", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Multi-Hop Mesh Routing Table
                    item {
                        Text(
                            text = "Active Mesh Routing Table (${peers.count { it.hopDistance > 0 }} reachable)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (peers.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No mesh routes learned yet. Once you pair and connect with a nearby CPI Social phone, 1-hop and multi-hop routes appear here automatically.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(peers, key = { it.nodeId }) { peer ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${peer.username} (${peer.nodeId})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (peer.routePath.isNotEmpty()) {
                                                    "Route: ${peer.routePath.joinToString(" → ")}"
                                                } else {
                                                    "Link offline"
                                                },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${peer.transportLabel} • Key #${peer.keyFingerprint}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = E2eeViolet
                                            )
                                        }
                                        MeshHopBadge(hopDistance = peer.hopDistance, compact = true)
                                    }
                                }
                            }
                        }
                    }

                    // Zero-Knowledge Relay Packet Audit Log
                    item {
                        Text(
                            text = "Zero-Knowledge Relay Packet Log",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (relayEvents.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "When this device relays encrypted packets between two out-of-range peers, relay headers and blocked decryption tests appear here.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        items(relayEvents.take(12), key = { it.id }) { ev ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Relay @${ev.relayNodeName} • Hop #${ev.hopNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MeshRelayAmber
                                        )
                                        Text(
                                            text = "TTL:${ev.ttlRemaining} • ${ev.packetType.name}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Header: ${ev.senderName} → ${ev.targetUsername} (pkt:${ev.packetId})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ciphertext: ${ev.ciphertextSnippet}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = E2eeViolet,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (!ev.relayCanDecrypt) "🔒 Relay Snooping Test: BLOCKED (AEADBadTagException)" else "Readable",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MeshDirectGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Voice Note Waveform Bubble inside Chat.
 */
@Composable
fun VoiceNoteBubbleContent(
    message: Message,
    isMe: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit
) {
    val bars = remember(message.waveformJson) {
        val parsed = message.waveformJson.split(",").mapNotNull { it.toFloatOrNull() }
        if (parsed.size >= 8) parsed else listOf(0.3f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.4f, 0.75f, 0.5f, 0.9f, 0.6f, 0.35f)
    }
    val fg = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 190.dp, max = 250.dp)
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.16f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                tint = fg
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bars.take(20).forEachIndexed { idx, amp ->
                    val barFrac = idx.toFloat() / bars.take(20).size.coerceAtLeast(1)
                    val active = isPlaying && barFrac <= progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((6 + amp * 18).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) MeshDirectGreen else fg.copy(alpha = if (isPlaying) 0.35f else 0.75f)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "🎙️ ${message.audioDurationSec}s • Opus/AAC E2EE",
                fontSize = 10.sp,
                color = fg.copy(alpha = 0.78f)
            )
        }
    }
}

/**
 * Compressed Photo Bubble decoded from local BLE mesh chunked transfer.
 */
@Composable
fun MeshImageBubbleContent(
    message: Message,
    isMe: Boolean
) {
    var showFullImage by remember { mutableStateOf(false) }
    val bitmap = remember(message.localFilePath) {
        message.localFilePath?.let { path ->
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }
    }

    Column {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Encrypted Mesh Photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullImage = true },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = "🖼️ ${message.content}",
            fontSize = 11.sp,
            color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showFullImage && bitmap != null) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(8.dp)) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Full Encrypted Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { showFullImage = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * E2EE Bluetooth Mesh Voice Call Overlay:
 * - Supports 1-Hop Direct Mode (Real-time PCM audio frame streaming)
 * - Auto-Fallbacks to Multi-Hop Walkie-Talkie (Push-to-Talk / PTT) when peer is 2+ hops away!
 */
@Composable
fun MeshCallOverlayScreen(viewModel: EchoViewModel) {
    val callState by viewModel.callState.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val isMuted by viewModel.isCallMuted.collectAsState()
    val durationSeconds by viewModel.callDuration.collectAsState()
    val transportMode by viewModel.callTransportMode.collectAsState()
    val safetyNumber by viewModel.callPeerSafetyNumber.collectAsState()
    val txFrames by viewModel.txAudioFrames.collectAsState()
    val rxFrames by viewModel.rxAudioFrames.collectAsState()
    val audioLevel by viewModel.callAudioLevel.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val isPttTransmitting by viewModel.isPttTransmitting.collectAsState()
    val isPttReceiving by viewModel.isPttReceiving.collectAsState()
    val pttStatus by viewModel.lastPttStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    if (callState == "idle" || activeCall == null) return

    val call = activeCall!!
    val isCallerMe = call.callerId == currentUser?.id
    val partnerId = if (isCallerMe) call.calleeId else call.callerId
    val partnerName = if (isCallerMe) call.calleeName else call.callerName
    val partnerAvatar = if (isCallerMe) call.calleeAvatarUrl else call.callerAvatarUrl
    val isDirect = transportMode == MeshCallTransportMode.DIRECT_STREAM

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F1A), Color(0xFF101828), Color(0xFF070B12))
                )
            )
            .testTag("call_overlay_container"),
        contentAlignment = Alignment.Center
    ) {
        MeshCallRippleAnimation(
            accentColor = if (isDirect) MeshDirectGreen else MeshRelayAmber,
            intensity = audioLevel
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top E2EE & Route Telemetry Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = E2eeViolet.copy(alpha = 0.22f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "E2EE Locked",
                            tint = E2eeViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "E2EE AES-256-GCM • Safety #${safetyNumber.take(11)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                MeshHopBadge(hopDistance = call.hopCount)

                Text(
                    text = when (callState) {
                        "ringing_incoming" -> "INCOMING OFFLINE MESH CALL"
                        "ringing_outgoing" -> if (isDirect) "DIALING DIRECT BLE..." else "ROUTING MULTI-HOP MESH INVITE..."
                        else -> if (isDirect) "DIRECT BLE VOICE STREAM" else "WALKIE-TALKIE PTT MODE"
                    },
                    color = if (isDirect) MeshDirectGreen else MeshRelayAmber,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                AsyncImage(
                    model = partnerAvatar,
                    contentDescription = "Peer Avatar",
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (isDirect) MeshDirectGreen else MeshRelayAmber, CircleShape),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = "@$partnerName",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = call.relayRoute.ifEmpty { "BLE Mesh Route" },
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (callState == "active") {
                    Text(
                        text = formatCallDuration(durationSeconds),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (isDirect) {
                        Text(
                            text = "16kHz PCM Encrypted Frames • TX:$txFrames  RX:$rxFrames",
                            color = MeshDirectGreen.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = if (isPttReceiving) "📡 RECEIVING RELAY VOICE BURST..." else pttStatus,
                            color = if (isPttTransmitting) Color(0xFFEF4444) else MeshRelayAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Controls Panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (callState == "ringing_incoming") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { viewModel.declineIncomingCall() },
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                                .testTag("call_decline_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, "Decline Call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .size(68.dp)
                                .background(MeshDirectGreen, CircleShape)
                                .testTag("call_accept_button")
                        ) {
                            Icon(Icons.Rounded.Call, "Accept Call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                } else {
                    // Multi-Hop Auto-Fallback Walkie-Talkie PTT Button
                    if (callState == "active" && !isDirect) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPttTransmitting) Color(0xFFEF4444)
                                    else MeshRelayAmber
                                )
                                .border(4.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            viewModel.startPttTransmit()
                                            tryAwaitRelease()
                                            viewModel.releasePttTransmit()
                                        }
                                    )
                                }
                                .testTag("ptt_hold_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Hold to Talk",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (isPttTransmitting) "TALKING" else "HOLD PTT",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleCallMute() },
                            modifier = Modifier
                                .size(54.dp)
                                .background(
                                    if (isMuted) Color.White else Color.White.copy(alpha = 0.14f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }

                        IconButton(
                            onClick = { viewModel.endActiveCall() },
                            modifier = Modifier
                                .size(68.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                                .testTag("call_end_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = { viewModel.toggleSpeakerphone() },
                            modifier = Modifier
                                .size(54.dp)
                                .background(
                                    if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.14f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Speakerphone",
                                tint = if (isSpeakerOn) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeshCallRippleAnimation(accentColor: Color, intensity: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshRipple")
    val scales = listOf(
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "r1"
        ),
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(900)
            ),
            label = "r2"
        )
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        scales.forEach { s ->
            val alpha = ((2.4f - s.value) / 1.4f).coerceIn(0f, 1f)
            val dynamicSize = (140 + intensity * 70) * s.value
            Box(
                modifier = Modifier
                    .size(dynamicSize.dp)
                    .border(2.dp, accentColor.copy(alpha = alpha * 0.6f), CircleShape)
                    .background(accentColor.copy(alpha = alpha * 0.12f), CircleShape)
            )
        }
    }
}
