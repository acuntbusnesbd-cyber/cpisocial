package com.example.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------
// BLUETOOTH-FIRST AUTHENTICATION SCREENS (NO DEMO ACCOUNTS, NO AUTO-LOGIN)
// -------------------------------------------------------------------------

@Composable
fun UserAvatarBadge(
    username: String,
    avatarUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$username Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = username.take(2).uppercase().ifBlank { "BT" },
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun BluetoothGateCard(
    viewModel: EchoViewModel,
    onOpenPairingHub: () -> Unit
) {
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val hasPerms by viewModel.hasBlePermissions.collectAsState()
    val bleStatus by viewModel.bleHardwareStatus.collectAsState()
    val localNodeId by viewModel.localNodeId.collectAsState()
    val fingerprint by viewModel.localKeyFingerprint.collectAsState()

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

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hasPerms && isBtEnabled) MeshDirectGreen.copy(alpha = 0.6f)
            else MeshRelayAmber.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasPerms && isBtEnabled) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothDisabled,
                        contentDescription = "Bluetooth Status",
                        tint = if (hasPerms && isBtEnabled) MeshDirectGreen else MeshRelayAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasPerms && isBtEnabled) "Bluetooth Radio Active" else "Bluetooth Setup Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = localNodeId,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "$bleStatus • E2EE #$fingerprint",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!hasPerms) {
                    Button(
                        onClick = { permissionLauncher.launch(requiredPermissions) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_bt_permissions_btn")
                    ) {
                        Text("Grant Bluetooth", fontSize = 12.sp)
                    }
                } else if (!isBtEnabled) {
                    Button(
                        onClick = { btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_bluetooth_btn")
                    ) {
                        Text("Turn On Bluetooth", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val disc = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                            }
                            discoverableLauncher.launch(disc)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discoverable", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenPairingHub,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pair Devices", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: EchoViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    val localAccounts by viewModel.localAccounts.collectAsState()
    var usernameInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showPairingDialog by remember { mutableStateOf(false) }
    var selectedAccountForPin by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshBlePermissionsAndRadio()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BluetoothConnected,
                    contentDescription = "CPI Social Bluetooth Mesh",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "CPI Social",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Bluetooth Mesh Identity • No Demo Accounts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            BluetoothGateCard(
                viewModel = viewModel,
                onOpenPairingHub = { showPairingDialog = true }
            )

            if (localAccounts.isNotEmpty()) {
                Text(
                    text = "Your Saved Bluetooth Nodes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                localAccounts.forEach { acc ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (acc.passwordHash.isBlank()) {
                                    viewModel.selectAndLoginLocalAccount(
                                        user = acc,
                                        pinCode = "",
                                        onSuccess = onLoginSuccess,
                                        onError = { errorMessage = it }
                                    )
                                } else {
                                    selectedAccountForPin = acc
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarBadge(
                                username = acc.username,
                                avatarUrl = acc.avatarUrl,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${acc.username}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${acc.nodeId} • Tap to connect via Bluetooth",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteLocalAccount(acc.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete Node",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text("Bluetooth Node Username or Node ID") },
                leadingIcon = { Icon(Icons.Default.Bluetooth, "Node Icon") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it },
                label = { Text("PIN / Passcode (if set)") },
                leadingIcon = { Icon(Icons.Default.Lock, "Lock Icon") },
                trailingIcon = {
                    IconButton(onClick = { isPinVisible = !isPinVisible }) {
                        Icon(
                            imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle PIN visibility"
                        )
                    }
                },
                visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Button(
                onClick = {
                    viewModel.loginWithBluetoothNode(
                        usernameOrNodeId = usernameInput,
                        pinCode = pinInput,
                        onSuccess = onLoginSuccess,
                        onError = { errorMessage = it }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.BluetoothConnected, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign In via Bluetooth", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = onNavigateToSignup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("navigate_to_signup"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create New Bluetooth Node", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPairingDialog) {
        MeshNetworkInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showPairingDialog = false }
        )
    }

    selectedAccountForPin?.let { acc ->
        var modalPin by remember { mutableStateOf("") }
        var modalErr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { selectedAccountForPin = null },
            title = { Text("Unlock @${acc.username}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter passcode to activate Bluetooth node ${acc.nodeId}:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = modalPin,
                        onValueChange = { modalPin = it },
                        label = { Text("Passcode") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (modalErr.isNotEmpty()) {
                        Text(modalErr, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.selectAndLoginLocalAccount(
                        user = acc,
                        pinCode = modalPin,
                        onSuccess = {
                            selectedAccountForPin = null
                            onLoginSuccess()
                        },
                        onError = { modalErr = it }
                    )
                }) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAccountForPin = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: EchoViewModel,
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf(viewModel.getDefaultBluetoothDeviceName()) }
    var pinCode by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showPairingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshBlePermissionsAndRadio()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create Bluetooth Node",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Your identity & ECDH keys stay on your device and pair over Bluetooth",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            BluetoothGateCard(
                viewModel = viewModel,
                onOpenPairingHub = { showPairingDialog = true }
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Node Username") },
                leadingIcon = { Icon(Icons.Default.Person, "User Icon") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_username_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = pinCode,
                onValueChange = { pinCode = it },
                label = { Text("Device PIN / Passcode (optional)") },
                leadingIcon = { Icon(Icons.Default.Lock, "Lock Icon") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_password_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Status / Bio") },
                leadingIcon = { Icon(Icons.Default.Info, "Bio Icon") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_bio_input"),
                maxLines = 2
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Button(
                onClick = {
                    viewModel.createBluetoothAccount(
                        username = username,
                        pinCode = pinCode,
                        bio = bio,
                        onSuccess = onSignupSuccess,
                        onError = { errorMessage = it }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("signup_submit_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.BluetoothConnected, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate & Sign In via Bluetooth", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already registered a node? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Back to Login",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .testTag("navigate_to_login")
                )
            }
        }
    }

    if (showPairingDialog) {
        MeshNetworkInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showPairingDialog = false }
        )
    }
}

// -------------------------------------------------------------------------
// HOME FEED TAB WITH FACEBOOK DESIGN & MESSENGER NOTES SYSTEM
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit,
    onNavigateToDMs: () -> Unit,
    onNavigateToCreatePost: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val posts by viewModel.feedPosts.collectAsState()
    val users by viewModel.allUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()

    var showCallPickerDialog by remember { mutableStateOf(false) }
    var showMeshInspector by remember { mutableStateOf(false) }

    val reachableCount = meshPeers.count { it.hopDistance > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App header with Echo Social branding, BLE Mesh status pill, and top-right Phone Call icon
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CPI Social",
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MeshDirectGreen.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clickable { showMeshInspector = true }
                            .testTag("mesh_inspector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = "BLE Mesh Network",
                                tint = MeshDirectGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "MESH • $reachableCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeshDirectGreen
                            )
                        }
                    }
                }
            },
            actions = {
                // Plus / Create Post button
                IconButton(onClick = onNavigateToCreatePost) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create Post",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Top-Right Phone / Mesh Call button (Direct 1-Hop or Multi-Hop PTT)
                IconButton(
                    onClick = { showCallPickerDialog = true },
                    modifier = Modifier.testTag("top_bar_phone_call_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = "Start Mesh Voice Call",
                            tint = MeshDirectGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Messenger Button
                IconButton(
                    onClick = onNavigateToDMs,
                    modifier = Modifier.testTag("messenger_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = "Messenger Conversations",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (unreadNotifications > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                // Settings & Privacy Button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings and Privacy",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (showCallPickerDialog) {
            MeshPeerCallPickerDialog(
                viewModel = viewModel,
                onDismiss = { showCallPickerDialog = false },
                onOpenBluetoothPairing = { showMeshInspector = true }
            )
        }

        if (showMeshInspector) {
            MeshNetworkInspectorDialog(
                viewModel = viewModel,
                onDismiss = { showMeshInspector = false }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. "What's on your mind?" Facebook Create Post Card
            if (currentUser != null) {
                item {
                    FacebookCreatePostCard(
                        currentUser = currentUser!!,
                        onClickCreate = onNavigateToCreatePost
                    )
                }
            }

            // 2. Messenger / Facebook Notes Tray
            item {
                FacebookNotesTray(
                    viewModel = viewModel,
                    onNavigateToChat = onNavigateToDMs
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 6.dp)
            }

            // 3. Posts feed
            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BluetoothSearching,
                                contentDescription = "Empty feed",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "No Mesh Posts Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Pair with nearby CPI Social phones over Bluetooth to sync posts, or create your first post!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showMeshInspector = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan & Pair Bluetooth")
                            }
                        }
                    }
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    val author = users.find { it.id == post.userId } ?: return@items
                    PostItem(
                        post = post,
                        author = author,
                        viewModel = viewModel,
                        onNavigateToProfile = onNavigateToProfile
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// FACEBOOK "WHAT'S ON YOUR MIND?" COMPONENT
// -------------------------------------------------------------------------

@Composable
fun FacebookCreatePostCard(
    currentUser: User,
    onClickCreate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClickCreate() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UserAvatarBadge(
                    username = currentUser.username,
                    avatarUrl = currentUser.avatarUrl,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "What's on your mind, ${currentUser.username}?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Video
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Live Video",
                        tint = Color(0xFFE41E3F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                // Photo/Video
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "Photo/Video",
                        tint = Color(0xFF45BD62),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                // Feeling/Activity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                        contentDescription = "Feeling/activity",
                        tint = Color(0xFFF7B928),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Feeling", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// MESSENGER / FACEBOOK NOTES TRAY & DIALOGS
// -------------------------------------------------------------------------

@Composable
fun FacebookNotesTray(
    viewModel: EchoViewModel,
    onNavigateToChat: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()

    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var showMeshInspector by remember { mutableStateOf(false) }
    var viewingNoteTarget by remember { mutableStateOf<Pair<User, UserNote>?>(null) }

    if (currentUser == null) return

    val myNote = allNotes.find { it.userId == currentUser!!.id }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Notes & Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showMeshInspector = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.BluetoothConnected,
                    contentDescription = "Offline Mesh",
                    tint = MeshDirectGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Offline Mesh Nodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // My Note Item
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(86.dp)
                        .clickable { showCreateNoteDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (myNote != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = myNote?.content?.ifEmpty { "+ Note" } ?: "+ Note",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (myNote != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatarBadge(
                            username = currentUser!!.username,
                            avatarUrl = currentUser!!.avatarUrl,
                            modifier = Modifier
                                .size(58.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (myNote != null) Icons.Rounded.Edit else Icons.Rounded.Add,
                                contentDescription = "Edit Note",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your Node",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "Local Host",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reachable Direct & Multi-Hop Peers with Hop Distance Indicators
            val otherUsers = allUsers.filter { it.id != currentUser!!.id }
            items(otherUsers, key = { it.id }) { user ->
                val userNote = allNotes.find { it.userId == user.id }
                val peer = meshPeers.find { it.userId == user.id }
                val hopDist = peer?.hopDistance ?: -1
                val ringColor = when {
                    hopDist == 1 -> MeshDirectGreen
                    hopDist >= 2 -> MeshRelayAmber
                    else -> MeshOfflineGray
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(90.dp)
                        .clickable {
                            viewingNoteTarget = Pair(
                                user,
                                userNote ?: UserNote(userId = user.id, content = peer?.hopLabel ?: "Offline Peer")
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (userNote != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = userNote?.content ?: (peer?.hopLabel ?: "Offline"),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                color = if (userNote != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    UserAvatarBadge(
                        username = user.username,
                        avatarUrl = user.avatarUrl,
                        modifier = Modifier
                            .size(58.dp)
                            .border(2.5.dp, ringColor, CircleShape)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    MeshHopBadge(hopDistance = hopDist, compact = true)
                }
            }
        }
    }

    if (showCreateNoteDialog) {
        CreateNoteDialog(
            currentNote = myNote,
            onSave = { content, music ->
                viewModel.saveNote(content, music)
                showCreateNoteDialog = false
            },
            onDelete = {
                viewModel.deleteNote()
                showCreateNoteDialog = false
            },
            onDismiss = { showCreateNoteDialog = false }
        )
    }

    if (showMeshInspector) {
        MeshNetworkInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showMeshInspector = false }
        )
    }

    viewingNoteTarget?.let { (targetUser, note) ->
        ViewNoteDialog(
            targetUser = targetUser,
            note = note,
            viewModel = viewModel,
            onSendReply = { replyMsg ->
                viewModel.replyToNote(targetUser.id, note.content, replyMsg) {
                    onNavigateToChat()
                }
                viewingNoteTarget = null
            },
            onDismiss = { viewingNoteTarget = null }
        )
    }
}

@Composable
fun CreateNoteDialog(
    currentNote: UserNote?,
    onSave: (content: String, music: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf(currentNote?.content ?: "") }
    var musicTrack by remember { mutableStateOf(currentNote?.musicTrack ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share a Note 💭",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Notes are visible to your friends at the top of inbox & feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Live Preview Bubble
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (content.isBlank()) "Share what's on your mind..." else content,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        if (musicTrack.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🎵 $musicTrack",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 60) content = it },
                    label = { Text("Note text (max 60 chars)") },
                    placeholder = { Text("e.g. Listening to synthwave 🎵") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = musicTrack,
                    onValueChange = { musicTrack = it },
                    label = { Text("Song or Audio Track (Optional)") },
                    placeholder = { Text("e.g. Lover • Taylor Swift") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Music Track",
                            tint = Color(0xFF1877F2)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentNote != null) {
                        TextButton(onClick = onDelete) {
                            Text("Delete Note", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (content.isNotBlank()) {
                                onSave(content.trim(), musicTrack.ifBlank { null })
                            }
                        },
                        enabled = content.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Share Note", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ViewNoteDialog(
    targetUser: User,
    note: UserNote,
    viewModel: EchoViewModel,
    onSendReply: (replyMessage: String) -> Unit,
    onDismiss: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val currentlyPlayingTrack by viewModel.currentlyPlayingTrack.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()

    val isThisTrackPlaying = currentlyPlayingTrack == note.musicTrack && isPlayingAudio
    val audioUrl = note.audioUrl ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

    Dialog(onDismissRequest = {
        viewModel.stopAudioTrack()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatarBadge(
                    username = targetUser.username,
                    avatarUrl = targetUser.avatarUrl,
                    modifier = Modifier
                        .size(72.dp)
                        .border(3.dp, Color(0xFF1877F2), CircleShape)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = targetUser.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "\"${note.content}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                        if (!note.musicTrack.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Interactive Audio Player Card
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.playAudioTrack(audioUrl, note.musicTrack)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1877F2)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isThisTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play/Pause Song",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = note.musicTrack,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF1877F2),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isThisTrackPlaying) "Playing live audio... 🎵" else "Tap to play song audio 🎧",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Send message to ${targetUser.username}...") },
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    viewModel.stopAudioTrack()
                                    onSendReply(replyText.trim())
                                }
                            },
                            enabled = replyText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Direct Message",
                                tint = if (replyText.isNotBlank()) Color(0xFF1877F2) else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = {
                    viewModel.stopAudioTrack()
                    onDismiss()
                }) {
                    Text("Close")
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostItem(
    post: Post,
    author: User,
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit
) {
    val isLiked by viewModel.isPostLikedByUser(post.id).collectAsState(initial = false)
    val likesCount by viewModel.getLikeCountForPost(post.id).collectAsState(initial = 0)
    val commentsCount by viewModel.getCommentCountForPost(post.id).collectAsState(initial = 0)
    var showCommentDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Author Avatar & Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile(author.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatarBadge(
                    username = author.username,
                    avatarUrl = author.avatarUrl,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = author.username,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        if (author.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatRelativeTime(post.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Public post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.0f))

                // Delete own posts
                if (post.userId == viewModel.currentUser.value?.id) {
                    IconButton(onClick = { viewModel.deletePost(post) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Post",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Caption
            Text(
                text = post.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            // Post Image / Media Content
            if (post.mediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.mediaUrls.first(),
                    contentDescription = "Post Media",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Facebook Style Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Likes count (with standard blue circle and white thumbs-up icon)
                if (likesCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ThumbUp,
                                contentDescription = "Likes",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = likesCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Comments count
                if (commentsCount > 0) {
                    Text(
                        text = "$commentsCount comment${if (commentsCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Symmetrical Action Buttons Row (Like, Comment, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.toggleLikePost(post.id) }
                        .padding(vertical = 8.dp)
                        .testTag("like_button_${post.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Like",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Comment Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showCommentDialog = true }
                        .padding(vertical = 8.dp)
                        .testTag("comment_button_${post.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comment",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Share Button
                var isShared by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isShared = true }
                        .padding(vertical = 8.dp)
                        .testTag("share_button_${post.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = if (isShared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isShared) "Shared!" else "Share",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isShared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

                // Call direct shortcut trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (post.userId != viewModel.currentUser.value?.id) {
                            viewModel.initiateCall(post.userId, "video")
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Call Poster",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

    if (showCommentDialog) {
        CommentDialog(
            postId = post.id,
            viewModel = viewModel,
            onDismiss = { showCommentDialog = false }
        )
    }
}

// -------------------------------------------------------------------------
// COMMENTS DIALOG (THREADED VIEWS)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentDialog(
    postId: Int,
    viewModel: EchoViewModel,
    onDismiss: () -> Unit
) {
    val comments by viewModel.getCommentsForPost(postId).collectAsState(initial = emptyList())
    val users by viewModel.allUsers.collectAsState()
    var newCommentText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close Dialog")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Comments List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (comments.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No comments yet. Start the resonance!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // First level comments
                            val rootComments = comments.filter { it.parentCommentId == null }
                            items(rootComments) { rootComment ->
                                val rootAuthor = users.find { it.id == rootComment.userId } ?: return@items
                                CommentItem(
                                    comment = rootComment,
                                    author = rootAuthor,
                                    viewModel = viewModel,
                                    level = 0
                                )

                                // Replies (Level 1 nested)
                                val replies = comments.filter { it.parentCommentId == rootComment.id }
                                replies.forEach { reply ->
                                    val replyAuthor = users.find { it.id == reply.userId } ?: return@forEach
                                    CommentItem(
                                        comment = reply,
                                        author = replyAuthor,
                                        viewModel = viewModel,
                                        level = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Bottom Comment Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("Write a comment...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input_field"),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotEmpty()) {
                                viewModel.addComment(postId, newCommentText)
                                newCommentText = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            .testTag("comment_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    author: User,
    viewModel: EchoViewModel,
    level: Int
) {
    val isLiked by viewModel.isCommentLikedByUser(comment.id).collectAsState(initial = false)
    val likesCount by viewModel.getLikeCountForComment(comment.id).collectAsState(initial = 0)
    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 36).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatarBadge(
            username = author.username,
            avatarUrl = author.avatarUrl,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (author.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Text(
                text = comment.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Inline reply action bar
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Like comment
                Row(
                    modifier = Modifier.clickable { viewModel.toggleLikeComment(comment.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like comment",
                        tint = if (isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    if (likesCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(likesCount.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Trigger nested reply
                if (level == 0) {
                    Text(
                        text = "Reply",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { isReplying = !isReplying }
                    )
                }
            }

            // Replying Input Container
            if (isReplying && level == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Reply to ${author.username}...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (replyText.isNotEmpty()) {
                                viewModel.addComment(
                                    postId = comment.postId,
                                    content = replyText,
                                    parentCommentId = comment.id
                                )
                                replyText = ""
                                isReplying = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Reply",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// SEARCH TAB (LIVE SEARCH, HASHTAGS & DISCOVER SYSTEM)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val userResults by viewModel.searchUserResults.collectAsState()
    val postResults by viewModel.searchPostResults.collectAsState()
    val allUsersList by viewModel.allUsers.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()
    val bleStatus by viewModel.bleHardwareStatus.collectAsState()

    var showMeshInspector by remember { mutableStateOf(false) }

    val currentUserId = viewModel.currentUser.value?.id ?: -1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Discover Mesh",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            actions = {
                TextButton(onClick = { showMeshInspector = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Hub,
                        contentDescription = "Mesh Inspector",
                        tint = MeshDirectGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Topology", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (showMeshInspector) {
            MeshNetworkInspectorDialog(
                viewModel = viewModel,
                onDismiss = { showMeshInspector = false }
            )
        }

        // Live Debounced Input
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("Search mesh peers, usernames or #tags...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search icon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, "Clear icon")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("search_input_field"),
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (query.length < 2) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clickable { showMeshInspector = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BluetoothSearching,
                                contentDescription = "BLE Mesh Status",
                                tint = MeshDirectGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bleStatus,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to scan, pair Bluetooth devices & inspect mesh routes",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    Text(
                        "Connected & Paired Mesh Peers",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                val discoverableUsers = allUsersList.filter { it.id != currentUserId }
                if (discoverableUsers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BluetoothSearching,
                                contentDescription = "No Peers",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No Paired Bluetooth Peers Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Tap below to scan nearby devices and pair via Bluetooth.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showMeshInspector = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Scan & Pair Bluetooth")
                            }
                        }
                    }
                } else {
                    items(discoverableUsers) { targetUser ->
                        val isFollowing by viewModel.isFollowing(targetUser.id).collectAsState(initial = false)
                        val peer = meshPeers.find { it.userId == targetUser.id || it.nodeId == targetUser.nodeId }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProfile(targetUser.id) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarBadge(
                                username = targetUser.username,
                                avatarUrl = targetUser.avatarUrl,
                                modifier = Modifier
                                    .size(46.dp)
                                    .border(
                                        2.dp,
                                        when {
                                            peer?.hopDistance == 1 -> MeshDirectGreen
                                            (peer?.hopDistance ?: 0) >= 2 -> MeshRelayAmber
                                            else -> MeshOfflineGray
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        targetUser.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    if (targetUser.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Verified",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MeshHopBadge(hopDistance = peer?.hopDistance ?: -1, compact = true)
                                    if (!peer?.routePath.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = peer!!.routePath.joinToString("→"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.initiateCall(targetUser.id) },
                                enabled = (peer?.hopDistance ?: -1) > 0
                            ) {
                                Icon(
                                    imageVector = if (peer?.hopDistance == 1) Icons.Rounded.Call else Icons.Rounded.RecordVoiceOver,
                                    contentDescription = "Mesh Voice Call",
                                    tint = if (peer?.hopDistance == 1) MeshDirectGreen else MeshRelayAmber
                                )
                            }

                            Button(
                                onClick = { viewModel.toggleFollow(targetUser.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isFollowing) "Linked" else "Connect", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Live Search Results
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (userResults.isNotEmpty()) {
                    item {
                        Text(
                            "Users",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(userResults) { matchUser ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProfile(matchUser.id) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarBadge(
                                username = matchUser.username,
                                avatarUrl = matchUser.avatarUrl,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("@${matchUser.username}", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (postResults.isNotEmpty()) {
                    item {
                        Text(
                            "Posts",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(postResults) { matchPost ->
                        val author = allUsersList.find { it.id == matchPost.userId } ?: return@items
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .clickable { onNavigateToProfile(author.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                if (matchPost.mediaUrls.isNotEmpty()) {
                                    AsyncImage(
                                        model = matchPost.mediaUrls.first(),
                                        contentDescription = "Post preview",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column {
                                    Text("@${author.username}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        matchPost.caption,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (userResults.isEmpty() && postResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No match found for '$query'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// CREATE POST TAB (PRESET AND PREVIEWS)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    viewModel: EchoViewModel,
    onPostCreated: () -> Unit
) {
    var captionText by remember { mutableStateOf("") }
    var inputImageUrl by remember { mutableStateOf("") }

    // List of premium preset wallpaper URLs for easy aesthetic post design
    val presets = listOf(
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&q=80&w=300" to "Beach Sunset",
        "https://images.unsplash.com/photo-1542273917363-3b1817f69a2d?auto=format&fit=crop&q=80&w=300" to "Mist Forest",
        "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&q=80&w=300" to "Galaxy Lights",
        "https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&q=80&w=300" to "Tech Setup"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .imePadding()
    ) {
        TopAppBar(
            title = { Text("Create Post", fontWeight = FontWeight.Black) },
            actions = {
                TextButton(
                    onClick = {
                        if (captionText.isNotEmpty()) {
                            viewModel.createPost(captionText, inputImageUrl)
                            captionText = ""
                            inputImageUrl = ""
                            onPostCreated()
                        }
                    },
                    modifier = Modifier.testTag("create_post_submit_button")
                ) {
                    Text("Publish", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = { Text("Write something beautiful... Include #hashtags") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("create_post_caption_input"),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = inputImageUrl,
                    onValueChange = { inputImageUrl = it },
                    placeholder = { Text("Optional image URL...") },
                    leadingIcon = { Icon(Icons.Default.Image, "Image Link icon") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_post_image_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Text("Or select an aesthetic preset wallpaper:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (url, label) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { inputImageUrl = url }
                                .border(
                                    width = if (inputImageUrl == url) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.height(80.dp)) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = label,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Preview Section
            if (inputImageUrl.isNotEmpty() || captionText.isNotEmpty()) {
                item {
                    Text("Preview Card:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (captionText.isEmpty()) "Your beautiful caption will appear here..." else captionText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (inputImageUrl.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AsyncImage(
                                    model = inputImageUrl,
                                    contentDescription = "Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// DIRECT MESSAGES TAB (CONVERSATIONS LIST & LIVE CHAT SIMULATION)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMsListScreen(
    viewModel: EchoViewModel,
    onNavigateToChat: () -> Unit,
    onBackPressed: (() -> Unit)? = null
) {
    val conversations by viewModel.conversations.collectAsState()
    val users by viewModel.allUsers.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()
    val currentUserId = viewModel.currentUser.value?.id ?: -1

    var showMeshInspector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mesh Chats", fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = E2eeViolet.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "🔒 E2EE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = E2eeViolet,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBackPressed != null) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { showMeshInspector = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Hub,
                        contentDescription = "Mesh Topology Inspector",
                        tint = MeshDirectGreen
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        if (showMeshInspector) {
            MeshNetworkInspectorDialog(
                viewModel = viewModel,
                onDismiss = { showMeshInspector = false }
            )
        }

        // Notes & Mesh Peers Carousel
        FacebookNotesTray(
            viewModel = viewModel,
            onNavigateToChat = onNavigateToChat
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (conversations.isNotEmpty()) {
                item {
                    Text(
                        text = "Encrypted Mesh Conversations",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(conversations, key = { it.id }) { conv ->
                    val otherParticipantId = conv.participantIds.find { it != currentUserId } ?: return@items
                    val partnerUser = users.find { it.id == otherParticipantId } ?: return@items
                    val peer = meshPeers.find { it.userId == otherParticipantId }
                    val lastMessageFlow = viewModel.getLastMessage(conv.id).collectAsState(initial = null)
                    val lastMessage = lastMessageFlow.value

                    val hopDist = peer?.hopDistance ?: -1
                    val ringColor = when {
                        hopDist == 1 -> MeshDirectGreen
                        hopDist >= 2 -> MeshRelayAmber
                        else -> MeshOfflineGray
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectConversation(conv.id)
                                onNavigateToChat()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarBadge(
                            username = partnerUser.username,
                            avatarUrl = partnerUser.avatarUrl,
                            modifier = Modifier
                                .size(48.dp)
                                .border(2.dp, ringColor, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    partnerUser.username,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                MeshHopBadge(hopDistance = hopDist, compact = true)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastMessage?.content ?: "Tap to start E2EE BLE chat...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lastMessage?.isRead == false && lastMessage.senderId != currentUserId) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (lastMessage?.isRead == false && lastMessage.senderId != currentUserId) FontWeight.ExtraBold else FontWeight.Normal
                            )
                        }

                        IconButton(onClick = { viewModel.initiateCall(partnerUser.id) }) {
                            Icon(
                                imageVector = if (hopDist == 1) Icons.Rounded.Call else Icons.Rounded.RecordVoiceOver,
                                contentDescription = "Start Mesh Call",
                                tint = ringColor
                            )
                        }
                    }
                }
            }

            val existingPartnerIds = conversations.flatMap { it.participantIds }.toSet()
            val unstartedPeers = meshPeers.filter { it.userId > 0 && it.userId !in existingPartnerIds }
            if (conversations.isEmpty() && unstartedPeers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothSearching,
                            contentDescription = "Pair Devices",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No Bluetooth Mesh Chats Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Pair and connect with another device running CPI Social to send E2EE text, photos, voice notes, and calls offline.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { showMeshInspector = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan & Pair Devices")
                        }
                    }
                }
            }
            if (unstartedPeers.isNotEmpty()) {
                item {
                    Text(
                        text = "Reachable Mesh Peers (Start E2EE Chat)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(unstartedPeers, key = { it.nodeId }) { peer ->
                    val usr = users.find { it.id == peer.userId || it.nodeId == peer.nodeId } ?: return@items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.startConversationWith(usr.id) { convId ->
                                    viewModel.selectConversation(convId)
                                    onNavigateToChat()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarBadge(
                            username = usr.username,
                            avatarUrl = usr.avatarUrl,
                            modifier = Modifier
                                .size(44.dp)
                                .border(
                                    2.dp,
                                    if (peer.hopDistance == 1) MeshDirectGreen else MeshRelayAmber,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(usr.username, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                MeshHopBadge(hopDistance = peer.hopDistance, compact = true)
                            }
                            Text(
                                text = if (peer.routePath.isNotEmpty()) "Route: ${peer.routePath.joinToString(" → ")}" else "Offline",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.initiateCall(usr.id) }) {
                            Icon(
                                imageVector = if (peer.hopDistance == 1) Icons.Rounded.Call else Icons.Rounded.RecordVoiceOver,
                                contentDescription = "Call Peer",
                                tint = if (peer.hopDistance == 1) MeshDirectGreen else MeshRelayAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMDetailScreen(
    viewModel: EchoViewModel,
    onBackPressed: () -> Unit
) {
    val messages by viewModel.activeMessages.collectAsState()
    val users by viewModel.allUsers.collectAsState()
    val convId by viewModel.activeConversationId.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()
    val activeTransfer by viewModel.activeMeshTransfer.collectAsState()

    val isRecordingVoiceNote by viewModel.isRecordingVoiceNote.collectAsState()
    val recordingDurationSec by viewModel.recordingDurationSec.collectAsState()
    val liveWaveformBars by viewModel.liveWaveformBars.collectAsState()
    val playingVoiceNoteId by viewModel.playingVoiceNoteId.collectAsState()
    val voiceNoteProgress by viewModel.voiceNoteProgress.collectAsState()

    var chatText by remember { mutableStateOf("") }
    var showSafetyNumberDialog by remember { mutableStateOf(false) }
    var showMeshInspector by remember { mutableStateOf(false) }
    val currentUserId = viewModel.currentUser.value?.id ?: -1

    if (convId == null) {
        onBackPressed()
        return
    }

    val conversation = remember { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(convId) {
        conversation.value = viewModel.getConversationById(convId!!)
    }

    val partnerId = conversation.value?.participantIds?.find { it != currentUserId }
    val partner = partnerId?.let { id -> users.find { it.id == id } }
    val peer = partnerId?.let { id -> meshPeers.find { it.userId == id } }

    // Android Photo Picker (Zero-permission Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.sendPhotoMessage(it) }
    }

    // Audio record permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Start recording cleanly even if user uses simulator audio fallback
        viewModel.startVoiceNoteRecording()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header with E2EE & Hop Distance + Call Button
        TopAppBar(
            title = {
                if (partner != null) {
                    val hopDist = peer?.hopDistance ?: -1
                    val ringColor = when {
                        hopDist == 1 -> MeshDirectGreen
                        hopDist >= 2 -> MeshRelayAmber
                        else -> MeshOfflineGray
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatarBadge(
                            username = partner.username,
                            avatarUrl = partner.avatarUrl,
                            modifier = Modifier
                                .size(38.dp)
                                .border(2.dp, ringColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    partner.username,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "E2EE Verified",
                                    tint = E2eeViolet,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { showSafetyNumberDialog = true }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MeshHopBadge(hopDistance = hopDist, compact = true)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        hopDist == 1 -> "Voice Ready"
                                        hopDist >= 2 -> "PTT Ready"
                                        else -> "Offline • Pair to Connect"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Text("Mesh Chat")
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back icon")
                }
            },
            actions = {
                if (partner != null) {
                    val hopDist = peer?.hopDistance ?: -1
                    val callTint = when {
                        hopDist == 1 -> MeshDirectGreen
                        hopDist >= 2 -> MeshRelayAmber
                        else -> MeshOfflineGray
                    }
                    IconButton(onClick = { showMeshInspector = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = "Mesh Inspector",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.initiateCall(partner.id) },
                        modifier = Modifier.testTag("in_chat_call_button")
                    ) {
                        Icon(
                            imageVector = if (hopDist == 1) Icons.Rounded.Call else Icons.Rounded.RecordVoiceOver,
                            contentDescription = "Mesh Voice Call",
                            tint = callTint
                        )
                    }
                }
            }
        )

        // E2EE Banner
        Surface(
            color = E2eeViolet.copy(alpha = 0.12f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSafetyNumberDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.VerifiedUser, "E2EE", tint = E2eeViolet, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ECDH P-256 + AES-256-GCM • Zero Relay Snooping • Tap to verify key",
                    fontSize = 11.sp,
                    color = E2eeViolet,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Active BLE Chunk Transfer Progress Banner
        if (activeTransfer != null && activeTransfer!!.recipientId == partnerId) {
            val transfer = activeTransfer!!
            val pct = (transfer.sentChunks.toFloat() / transfer.totalChunks.coerceAtLeast(1)).coerceIn(0f, 1f)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Encrypting & Sending BLE Chunks (${transfer.sentChunks}/${transfer.totalChunks})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${transfer.hopDistance} Hop${if (transfer.hopDistance > 1) "s" else ""}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Chat Message Log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.widthIn(max = 278.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                when (msg.messageType) {
                                    "image" -> MeshImageBubbleContent(message = msg, isMe = isMe)
                                    "voice_note" -> VoiceNoteBubbleContent(
                                        message = msg,
                                        isMe = isMe,
                                        isPlaying = playingVoiceNoteId == msg.id,
                                        progress = if (playingVoiceNoteId == msg.id) voiceNoteProgress else 0f,
                                        onTogglePlay = { viewModel.togglePlayVoiceNote(msg) }
                                    )
                                    else -> Text(
                                        text = msg.content,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (msg.isEncrypted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "E2EE",
                                            tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else E2eeViolet,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${msg.hopCount}H • ${formatTimeOnly(msg.createdAt)}",
                                        fontSize = 9.sp,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = when (msg.meshStatus) {
                                                "delivered" -> Icons.Rounded.DoneAll
                                                "offline" -> Icons.Rounded.CloudOff
                                                else -> Icons.Rounded.Done
                                            },
                                            contentDescription = "Mesh Status: ${msg.meshStatus}",
                                            tint = when (msg.meshStatus) {
                                                "delivered" -> MeshDirectGreen
                                                "offline" -> MeshRelayAmber
                                                else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            },
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Composer: Photo Picker + Text Input + Hold-to-Record Voice Note Mic
        if (isRecordingVoiceNote) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.stopAndSendVoiceNote(cancel = true) }) {
                        Icon(Icons.Rounded.Delete, "Cancel Recording", tint = MaterialTheme.colorScheme.error)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%02d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        liveWaveformBars.takeLast(20).forEach { amp ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((6 + amp * 20).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.stopAndSendVoiceNote(cancel = false) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MeshDirectGreen, CircleShape)
                            .testTag("send_voice_note_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send Voice Note", tint = Color.White)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zero-permission Photo Picker -> compresses & sends encrypted BLE chunks
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("chat_photo_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "Send Encrypted Photo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    placeholder = { Text("Encrypted BLE message...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (chatText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            viewModel.sendChatMessage(chatText.trim())
                            chatText = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Direct Message",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    // Hold-to-record (or tap-to-record) Voice Note Mic button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MeshDirectGreen)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onLongPress = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                )
                            }
                            .testTag("chat_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Record Voice Note",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showMeshInspector) {
        MeshNetworkInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showMeshInspector = false }
        )
    }

    if (showSafetyNumberDialog && partner != null) {
        val safety = viewModel.getSafetyNumberForUser(partner.id)
        AlertDialog(
            onDismissRequest = { showSafetyNumberDialog = false },
            title = { Text("E2EE Safety Verification", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Messages, photos, and voice calls with @${partner.username} are end-to-end encrypted using ECDH P-256 + AES-256-GCM.",
                        fontSize = 13.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = safety,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Text(
                        text = "Intermediate relay nodes only see packetId, recipientId, and TTL. Payload decryption on relays is cryptographically impossible.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSafetyNumberDialog = false }) { Text("Verified") }
            }
        )
    }
}

@Composable
fun DotLoading() {
    val infiniteTransition = rememberInfiniteTransition()
    val dotCount = 3
    val scales = List(dotCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 200)
            )
        )
    }
    scales.forEach { scale ->
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = scale.value))
        )
    }
}

// -------------------------------------------------------------------------
// NOTIFICATIONS BELL TAB (LIVE LIST & STATE TRIGGERS)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit
) {
    val list by viewModel.notifications.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Activity Log", fontWeight = FontWeight.Black) },
            actions = {
                TextButton(onClick = { viewModel.markAllNotificationsAsRead() }) {
                    Text("Clear unread", fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = "No notifications",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text("No activities recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(list, key = { it.id }) { notif ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (notif.isRead) Color.Transparent else MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.05f
                                )
                            )
                            .clickable {
                                viewModel.markNotificationAsRead(notif.id)
                                if (notif.type == "follow") {
                                    onNavigateToProfile(notif.actorId)
                                } else {
                                    // Navigate to feed profile
                                    onNavigateToProfile(notif.actorId)
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = notif.actorAvatarUrl,
                            contentDescription = "Actor avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val detailText = when (notif.type) {
                                "like" -> "liked your post"
                                "comment" -> "commented on your post"
                                "follow" -> "started following you"
                                "message" -> "sent you a message"
                                "call" -> "called you"
                                else -> "resonated with you"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "@${notif.actorName} ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = detailText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatRelativeTime(notif.createdAt),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // Badge for activity icons
                        Icon(
                            imageVector = when (notif.type) {
                                "like" -> Icons.Filled.Favorite
                                "comment" -> Icons.Rounded.ChatBubble
                                "follow" -> Icons.Rounded.PersonAdd
                                "message" -> Icons.Rounded.Chat
                                else -> Icons.Rounded.Notifications
                            },
                            contentDescription = "Activity type logo",
                            tint = if (notif.type == "like") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// PROFILE TAB (EDITABLE BIO, COVER, FOLLOWERS LIST, PRIVATE TOGGLES, CALL LOGS)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: EchoViewModel,
    viewedUserId: Int,
    onNavigateToChat: () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    LaunchedEffect(viewedUserId) {
        viewModel.setViewedProfile(viewedUserId)
    }

    val user by viewModel.viewedUser.collectAsState()
    val posts by viewModel.viewedUserPosts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val currentUserId = currentUser?.id ?: -1
    val isMe = viewedUserId == currentUserId
    val calls by viewModel.callHistory.collectAsState(initial = emptyList())

    var isEditMode by remember { mutableStateOf(false) }
    var editBioText by remember { mutableStateOf("") }
    var isPrivateState by remember { mutableStateOf(false) }

    var showFollowersDialog by remember { mutableStateOf(false) }
    var showFollowingDialog by remember { mutableStateOf(false) }
    var showChangePictureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        user?.let {
            editBioText = it.bio
            isPrivateState = it.isPrivate
        }
    }

    if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isFollowing by viewModel.isFollowing(viewedUserId).collectAsState(initial = false)
    val followCounts by viewModel.getFollowCounts(viewedUserId).collectAsState(initial = Pair(0, 0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        TopAppBar(
            title = { Text("@${user!!.username}", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                if (onBackPressed != null) {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back arrow")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = {
                if (isMe) {
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Rounded.Settings, "Settings & Privacy", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Rounded.Logout, "Log out")
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Profile Cover Photo Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    // Cover photo (180dp)
                    AsyncImage(
                        model = if (user!!.coverUrl.isNotEmpty()) user!!.coverUrl else "https://images.unsplash.com/photo-1557683316-973673baf926?auto=format&fit=crop&q=80&w=600",
                        contentDescription = "Cover Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                )
                            )
                    )

                    // Edit Cover Photo Badge (if isMe)
                    if (isMe) {
                        Surface(
                            onClick = { showChangePictureDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp)
                                .testTag("edit_cover_photo_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CameraAlt, "Edit Cover Photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Cover", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Centered User Profile Avatar overlapping banner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable(enabled = isMe) { showChangePictureDialog = true }
                            .testTag("profile_avatar_clickable")
                    ) {
                        UserAvatarBadge(
                            username = user!!.username,
                            avatarUrl = user!!.avatarUrl,
                            modifier = Modifier
                                .size(110.dp)
                                .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                        if (isMe) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1877F2))
                                    .border(2.dp, Color.White, CircleShape)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "Change Profile Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // User Name and Bio (Centered)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title and Verification badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user!!.username,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (user!!.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verified profile badge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Username Tag
                    Text(
                        text = "@${user!!.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Active Note Status Badge
                    val allNotes by viewModel.allNotes.collectAsState()
                    val userNote = allNotes.find { it.userId == viewedUserId }
                    if (userNote != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💭 ${userNote.content}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (!userNote.musicTrack.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• 🎵 ${userNote.musicTrack}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1877F2),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bio display/edit container
                    if (isEditMode) {
                        OutlinedTextField(
                            value = editBioText,
                            onValueChange = { editBioText = it },
                            placeholder = { Text("Write something about yourself...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_edit_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Private Account", fontWeight = FontWeight.Bold)
                            Switch(
                                checked = isPrivateState,
                                onCheckedChange = { isPrivateState = it },
                                modifier = Modifier.testTag("private_toggle")
                            )
                        }
                    } else {
                        Text(
                            text = if (user!!.bio.isEmpty()) "No bio written yet..." else user!!.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Facebook-style Pill Actions Row
                    if (isMe) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Add to Story (Blue Facebook Button)
                            Button(
                                onClick = { /* Story simulator action */ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Add, "Add story", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add to Story", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Edit Profile (Grey Facebook Button)
                            Button(
                                onClick = {
                                    if (isEditMode) {
                                        viewModel.updateProfile(editBioText, isPrivateState)
                                    }
                                    isEditMode = !isEditMode
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                        contentDescription = "Edit Profile",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEditMode) "Save Changes" else "Edit Profile", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Follow Button (Pill)
                            Button(
                                onClick = { viewModel.toggleFollow(viewedUserId) },
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isFollowing) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                        contentDescription = "Follow Action",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFollowing) "Following" else "Follow", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Send Message (Messenger Pill)
                            Button(
                                onClick = {
                                    viewModel.startConversationWith(viewedUserId) { newConvId ->
                                        viewModel.selectConversation(newConvId)
                                        onNavigateToChat()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Chat, "Messenger Chat", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Message", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Quick Call (Circular Shortcut)
                            IconButton(
                                onClick = { viewModel.initiateCall(viewedUserId, "video") },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Videocam, "Video Call", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            // Facebook Style Intro About Details Card
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "Intro Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Lives in
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Home, "Lives in", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Lives in ", color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "Dhaka, Bangladesh", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // From Location
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.LocationOn, "From", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "From ", color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "Dhaka, Bangladesh", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Followed by
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.RssFeed, "Followed by", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Followed by ", color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${12450 + followCounts.first} people",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Joined date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.CalendarToday, "Joined", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Joined ", color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "July 2026", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Friends & Connections Dialog Triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileCountItem(
                            count = posts.size,
                            label = "Posts"
                        )
                        ProfileCountItem(
                            count = followCounts.first,
                            label = "Followers",
                            onClick = { showFollowersDialog = true }
                        )
                        ProfileCountItem(
                            count = followCounts.second,
                            label = "Following",
                            onClick = { showFollowingDialog = true }
                        )
                    }
                }
            }

            // Posts Feed Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Posts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Grid/List posts listing
            if (user!!.isPrivate && !isMe && !isFollowing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Lock, "Private profile", modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("This Account is Private", fontWeight = FontWeight.Bold)
                            Text("Follow @${user!!.username} to view posts.", textAlign = TextAlign.Center)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No posts published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Vertical feed of beautiful full cards, just like real Facebook profiles!
                items(posts, key = { it.id }) { post ->
                    PostItem(
                        post = post,
                        author = user!!,
                        viewModel = viewModel,
                        onNavigateToProfile = {}
                    )
                }
            }

            // If it's Me, display active Call Logs history directly under grid
            if (isMe) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Divider()
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "History Log",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "CALL HISTORY LOGS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (calls.isEmpty()) {
                    item {
                        Text(
                            "No call records logged.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    items(calls) { call ->
                        val isCaller = call.callerId == currentUserId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (call.type == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                                    contentDescription = "Call type indicator",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val logTitle = if (isCaller) "To @${call.calleeName}" else "From @${call.callerName}"
                                Text(logTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusIcon = when (call.status) {
                                        "completed" -> Icons.Rounded.CallMade
                                        "missed" -> Icons.Rounded.CallMissed
                                        else -> Icons.Rounded.CallMissedOutgoing
                                    }
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = call.status,
                                        tint = if (call.status == "completed") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${call.status.capitalize()} • ${formatCallDuration(call.durationSeconds)} • ${formatRelativeTime(call.startedAt)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // dialog helper lists
    if (showFollowersDialog) {
        FollowListDialog(
            title = "Followers",
            viewModel = viewModel,
            userId = viewedUserId,
            isFollowers = true,
            onDismiss = { showFollowersDialog = false }
        )
    }

    if (showFollowingDialog) {
        FollowListDialog(
            title = "Following",
            viewModel = viewModel,
            userId = viewedUserId,
            isFollowers = false,
            onDismiss = { showFollowingDialog = false }
        )
    }

    if (showChangePictureDialog && user != null) {
        ChangeProfilePictureDialog(
            user = user!!,
            viewModel = viewModel,
            onDismiss = { showChangePictureDialog = false }
        )
    }
}

@Composable
fun ProfileCountItem(
    count: Int,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListDialog(
    title: String,
    viewModel: EchoViewModel,
    userId: Int,
    isFollowers: Boolean,
    onDismiss: () -> Unit
) {
    val listFlow = if (isFollowers) {
        viewModel.getFollowersFlow(userId)
    } else {
        viewModel.getFollowingFlow(userId)
    }
    val usersList by listFlow.collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (usersList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("List is empty.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(usersList) { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatarBadge(
                                    username = user.username,
                                    avatarUrl = user.avatarUrl,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.username, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Extension to fetch flow list directly in Composable safely
fun EchoViewModel.getFollowersFlow(userId: Int) = repository.getFollowers(userId)
fun EchoViewModel.getFollowingFlow(userId: Int) = repository.getFollowing(userId)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeProfilePictureDialog(
    user: User,
    viewModel: EchoViewModel,
    onDismiss: () -> Unit
) {
    var selectedAvatarUrl by remember { mutableStateOf(user.avatarUrl) }
    var selectedCoverUrl by remember { mutableStateOf(user.coverUrl) }
    var customAvatarInput by remember { mutableStateOf("") }
    var customCoverInput by remember { mutableStateOf("") }

    val avatarPresets = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200" to "Fashion Portrait",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=200" to "Modern Gent",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200" to "Smile Portrait",
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200" to "Tech Guy",
        "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&q=80&w=200" to "Aesthetic Girl",
        "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&q=80&w=200" to "Creative Male",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&q=80&w=200" to "Urban Style",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&q=80&w=200" to "Model Portrait"
    )

    val coverPresets = listOf(
        "https://images.unsplash.com/photo-1557683316-973673baf926?auto=format&fit=crop&q=80&w=600" to "Gradient Wave",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&q=80&w=600" to "Sunset Beach",
        "https://images.unsplash.com/photo-1542273917363-3b1817f69a2d?auto=format&fit=crop&q=80&w=600" to "Mist Forest",
        "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&q=80&w=600" to "Deep Galaxy",
        "https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&q=80&w=600" to "Starry Mountain",
        "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&q=80&w=600" to "Neon Cyberpunk"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Change Profile & Cover", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Card
                    item {
                        Text("Live Preview", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.height(150.dp).fillMaxWidth()) {
                                AsyncImage(
                                    model = selectedCoverUrl,
                                    contentDescription = "Cover preview",
                                    modifier = Modifier.fillMaxWidth().height(110.dp),
                                    contentScale = ContentScale.Crop
                                )
                                AsyncImage(
                                    model = selectedAvatarUrl,
                                    contentDescription = "Avatar preview",
                                    modifier = Modifier
                                        .size(70.dp)
                                        .align(Alignment.BottomCenter)
                                        .clip(CircleShape)
                                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Section 1: Select Profile Picture
                    item {
                        Text("Choose Profile Avatar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(avatarPresets) { (url, label) ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedAvatarUrl = url }
                                        .border(
                                            width = if (selectedAvatarUrl == url) 3.dp else 0.dp,
                                            color = Color(0xFF1877F2),
                                            shape = CircleShape
                                        )
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customAvatarInput,
                            onValueChange = {
                                customAvatarInput = it
                                if (it.isNotBlank()) selectedAvatarUrl = it
                            },
                            placeholder = { Text("Or paste custom avatar image URL...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Image, "Avatar image icon") }
                        )
                    }

                    // Section 2: Select Cover Photo
                    item {
                        Text("Choose Cover Banner", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(coverPresets) { (url, label) ->
                                Card(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(75.dp)
                                        .clickable { selectedCoverUrl = url }
                                        .border(
                                            width = if (selectedCoverUrl == url) 3.dp else 0.dp,
                                            color = Color(0xFF1877F2),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = label,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f))
                                        )
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customCoverInput,
                            onValueChange = {
                                customCoverInput = it
                                if (it.isNotBlank()) selectedCoverUrl = it
                            },
                            placeholder = { Text("Or paste custom cover image URL...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Wallpaper, "Cover image icon") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.updateUserAvatarAndCover(selectedAvatarUrl, selectedCoverUrl)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Check, "Save", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// P2P WEBRTC SIGNALING VOICE & VIDEO CALL OVERLAYS
// -------------------------------------------------------------------------

@Composable
fun CallOverlayScreen(viewModel: EchoViewModel) {
    val callState by viewModel.callState.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val isMuted by viewModel.isCallMuted.collectAsState()
    val isCameraOn by viewModel.isCallCameraOn.collectAsState()
    val durationSeconds by viewModel.callDuration.collectAsState()

    if (callState == "idle" || activeCall == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19)) // Custom Dark Space color for clean calling atmosphere
            .testTag("call_overlay_container"),
        contentAlignment = Alignment.Center
    ) {
        // Soundwave / Camera backdrop background animation
        if (callState == "active" && isCameraOn && activeCall!!.type == "video") {
            // Simulated local video feed using preset wallpapers
            AsyncImage(
                model = activeCall!!.calleeAvatarUrl.ifEmpty { "https://images.unsplash.com/photo-1557683316-973673baf926?auto=format&fit=crop&q=80&w=600" },
                contentDescription = "Video Feed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Overlay dim shade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        } else {
            // Circular ripple animations representing active audio sound waves
            CallRippleAnimation()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header information
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = if (callState == "ringing_incoming") "INCOMING CALL" else if (callState == "ringing_outgoing") "DIALING..." else "ON THE WIRE",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar
                UserAvatarBadge(
                    username = activeCall!!.callerName.ifEmpty { activeCall!!.calleeName },
                    avatarUrl = activeCall!!.callerAvatarUrl.ifEmpty { activeCall!!.calleeAvatarUrl },
                    modifier = Modifier
                        .size(100.dp)
                        .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                )

                Text(
                    text = "@" + activeCall!!.callerName.ifEmpty { activeCall!!.calleeName },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )

                if (callState == "active") {
                    Text(
                        text = formatCallDuration(durationSeconds),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Controls bottom panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                if (callState == "ringing_incoming") {
                    // Accept and Decline Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Decline
                        IconButton(
                            onClick = { viewModel.declineIncomingCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                                .testTag("call_decline_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, "Decline Call", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Accept
                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                                .testTag("call_accept_button")
                        ) {
                            Icon(Icons.Rounded.Call, "Accept Call", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                } else {
                    // Active call controls (Mute, Camera toggler, End call)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute toggle
                        IconButton(
                            onClick = { viewModel.toggleCallMute() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(if (isMuted) Color.White else Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }

                        // End Call Red Button
                        IconButton(
                            onClick = { viewModel.endActiveCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                                .testTag("call_end_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Camera toggle for video call
                        if (activeCall!!.type == "video") {
                            IconButton(
                                onClick = { viewModel.toggleCallCamera() },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(if (isCameraOn) Color.White.copy(alpha = 0.1f) else Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                    contentDescription = "Camera Toggle",
                                    tint = if (isCameraOn) Color.White else Color.Black
                                )
                            }
                        } else {
                            // Dummy placeholder spacer to preserve control symmetry
                            Spacer(modifier = Modifier.size(50.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallRippleAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val scales = listOf(
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart
            )
        ),
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(1000)
            )
        )
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        scales.forEach { scale ->
            val alpha = (2.4f - scale.value) / 1.4f
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = alpha), CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha * 0.15f), CircleShape)
            )
        }
    }
}

// -------------------------------------------------------------------------
// REUSABLE FORMATTERS AND HELPER FUNCTIONS
// -------------------------------------------------------------------------

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}

fun formatTimeOnly(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

fun formatCallDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// -------------------------------------------------------------------------
// REELS SYSTEM (FULL SCREEN SHORT VIDEOS)
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit
) {
    val allReels by viewModel.allReels.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    var showCreateReelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Echo Reels 🎬",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color(0xFF1877F2)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE7F3FF)
                        ) {
                            Text(
                                text = "HD Video",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1877F2),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateReelDialog = true },
                        modifier = Modifier.testTag("create_reel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoCall,
                            contentDescription = "Create Reel",
                            tint = Color(0xFF1877F2),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (allReels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = "No Reels",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No Reels yet. Be the first to share one!",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { showCreateReelDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
                        ) {
                            Text("Create Reel", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(allReels, key = { it.id }) { reel ->
                        val author = allUsers.find { it.id == reel.userId }
                        ReelCardItem(
                            reel = reel,
                            author = author,
                            viewModel = viewModel,
                            onNavigateToProfile = onNavigateToProfile
                        )
                    }
                }
            }
        }
    }

    if (showCreateReelDialog) {
        CreateReelDialog(
            viewModel = viewModel,
            onDismiss = { showCreateReelDialog = false }
        )
    }
}

@Composable
fun ReelCardItem(
    reel: Reel,
    author: User?,
    viewModel: EchoViewModel,
    onNavigateToProfile: (Int) -> Unit
) {
    val isLiked by viewModel.isReelLikedByUser(reel.id).collectAsState(initial = false)
    val likesCount by viewModel.getReelLikesCount(reel.id).collectAsState(initial = 0)
    val currentlyPlayingTrack by viewModel.currentlyPlayingTrack.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val isThisTrackPlaying = currentlyPlayingTrack == reel.audioTrackName && isPlayingAudio

    var showCommentDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(580.dp)
            .background(Color.DarkGray)
    ) {
        // Full screen video/image media representation
        AsyncImage(
            model = reel.videoUrl,
            contentDescription = "Reel Video",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for bottom readable text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 200f
                    )
                )
        )

        // Top reel HD tag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "REEL • LIVE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Audio Play/Pause Button on top corner
            IconButton(
                onClick = {
                    val audioUrl = reel.audioUrl ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                    viewModel.playAudioTrack(audioUrl, reel.audioTrackName)
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isThisTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play Audio Track",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right side floating action column (Like, Comment, Share, Sound)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Author Avatar with plus follow badge
            Box(contentAlignment = Alignment.BottomEnd) {
                UserAvatarBadge(
                    username = author?.username ?: "BT",
                    avatarUrl = author?.avatarUrl ?: "",
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { author?.let { onNavigateToProfile(it.id) } }
                )
            }

            // Like Reel Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { viewModel.toggleLikeReel(reel.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like Reel",
                        tint = if (isLiked) Color(0xFFE41E3F) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = likesCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comment Reel Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { showCommentDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubble,
                        contentDescription = "Comment Reel",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Comments",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Share Button
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share Reel",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Details Area (Author, Caption, Music Track)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.8f)
                .padding(start = 16.dp, bottom = 24.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { author?.let { onNavigateToProfile(it.id) } }
            ) {
                Text(
                    text = "@${author?.username ?: "user"}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (author?.isVerified == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1877F2),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = reel.caption,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Audio track indicator row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.clickable {
                    val audioUrl = reel.audioUrl ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                    viewModel.playAudioTrack(audioUrl, reel.audioTrackName)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Music",
                        tint = Color(0xFF1877F2),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isThisTrackPlaying) "▶ ${reel.audioTrackName}" else reel.audioTrackName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showCommentDialog) {
        Dialog(onDismissRequest = { showCommentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Reel Comments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nice Reel video! Loved the background soundtrack 🎵✨", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCommentDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateReelDialog(
    viewModel: EchoViewModel,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var audioTrackName by remember { mutableStateOf("") }

    val presetThumbnails = listOf(
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&q=80&w=600",
        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&q=80&w=600",
        "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&q=80&w=600"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create Echo Reel 🎬",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Reel Caption") },
                    placeholder = { Text("e.g. Sunset vibes in 4K 🏔️") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = audioTrackName,
                    onValueChange = { audioTrackName = it },
                    label = { Text("Song / Audio Track Name") },
                    placeholder = { Text("e.g. Lover • Taylor Swift") },
                    leadingIcon = { Icon(Icons.Rounded.MusicNote, "Music") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Video/Media URL (Optional)") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Or tap to pick a sample video style:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetThumbnails.forEachIndexed { idx, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Preset",
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    2.dp,
                                    if (videoUrl == url) Color(0xFF1877F2) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { videoUrl = url },
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (caption.isNotBlank()) {
                                viewModel.createReel(
                                    caption = caption.trim(),
                                    videoUrl = videoUrl.ifBlank { presetThumbnails.first() },
                                    audioTrackName = audioTrackName.ifBlank { "Original Audio - CPI Social 🎵" }
                                )
                                onDismiss()
                            }
                        },
                        enabled = caption.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
                    ) {
                        Text("Post Reel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// FACEBOOK SETTINGS & PRIVACY HUB SCREEN
// -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookSettingsScreen(
    viewModel: EchoViewModel,
    onBackPressed: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val hideReactionCounts by viewModel.hideReactionCounts.collectAsState()
    val isProfileLocked by viewModel.isProfileLocked.collectAsState()
    val showActiveStatus by viewModel.showActiveStatus.collectAsState()
    val dataSaverEnabled by viewModel.dataSaverEnabled.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showAccountSwitchDialog by remember { mutableStateOf(false) }
    var showActivityLogDialog by remember { mutableStateOf(false) }
    var showMetaVerifyDialog by remember { mutableStateOf(false) }
    var showHelpCenterDialog by remember { mutableStateOf(false) }
    var showSavedItemsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Privacy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpCenterDialog = true }) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = "Help")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meta Account Center Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1877F2),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("∞", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Meta Account Center",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Control settings across Facebook, Echo & Messenger",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        SettingsRowItem(
                            icon = Icons.Outlined.Person,
                            title = "Personal Details",
                            subtitle = "Contact info, birthday, and identity verification",
                            onClick = { showMetaVerifyDialog = true }
                        )

                        SettingsRowItem(
                            icon = Icons.Outlined.Lock,
                            title = "Password and Security",
                            subtitle = "Two-factor authentication & saved login alerts",
                            onClick = { showMetaVerifyDialog = true }
                        )

                        SettingsRowItem(
                            icon = Icons.Outlined.Verified,
                            title = "Meta Verified Badge",
                            subtitle = "Get a verified blue checkmark for your profile",
                            onClick = { showMetaVerifyDialog = true }
                        )
                    }
                }
            }

            // Preferences Section
            item {
                Text(
                    text = "Preferences",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsToggleItem(
                            icon = Icons.Outlined.DarkMode,
                            title = "Dark Mode",
                            subtitle = if (isDarkMode) "On" else "Off",
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode() }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            icon = Icons.Outlined.ThumbUp,
                            title = "Reaction Preferences",
                            subtitle = "Hide reaction counts on posts",
                            checked = hideReactionCounts,
                            onCheckedChange = { viewModel.toggleHideReactions() }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            icon = Icons.Outlined.WifiTetheringOff,
                            title = "Data Saver Mode",
                            subtitle = "Reduce HD video streaming data usage",
                            checked = dataSaverEnabled,
                            onCheckedChange = { viewModel.toggleDataSaver() }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsRowItem(
                            icon = Icons.Outlined.Language,
                            title = "Language and Region",
                            subtitle = appLanguage,
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }
            }

            // Audience and Visibility Section
            item {
                Text(
                    text = "Audience and Visibility",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsToggleItem(
                            icon = Icons.Outlined.Security,
                            title = "Profile Locking",
                            subtitle = if (isProfileLocked) "Only friends can see photos & posts" else "Public profile access enabled",
                            checked = isProfileLocked,
                            onCheckedChange = { viewModel.toggleProfileLock() }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsToggleItem(
                            icon = Icons.Outlined.Visibility,
                            title = "Active Status Indicator",
                            subtitle = "Show green dot when you're active online",
                            checked = showActiveStatus,
                            onCheckedChange = { viewModel.toggleActiveStatus() }
                        )
                    }
                }
            }

            // Your Information Section
            item {
                Text(
                    text = "Your Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Outlined.History,
                            title = "Activity Log",
                            subtitle = "View and manage your search and post interactions",
                            onClick = { showActivityLogDialog = true }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsRowItem(
                            icon = Icons.Outlined.BookmarkBorder,
                            title = "Saved Items & Bookmarks",
                            subtitle = "Posts, reels, and notes saved for later",
                            onClick = { showSavedItemsDialog = true }
                        )
                    }
                }
            }

            // Help & Community
            item {
                Text(
                    text = "Help & Community",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsRowItem(
                            icon = Icons.Outlined.ReportProblem,
                            title = "Report a Problem",
                            subtitle = "Shake device or send instant feedback to Meta support",
                            onClick = { showReportDialog = true }
                        )

                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsRowItem(
                            icon = Icons.Outlined.HelpCenter,
                            title = "Help Center & FAQs",
                            subtitle = "Frequently asked questions & safety guidelines",
                            onClick = { showHelpCenterDialog = true }
                        )
                    }
                }
            }

            // Bluetooth & Account Actions
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showAccountSwitchDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BluetoothConnected, contentDescription = "Bluetooth Pairing", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bluetooth Pairing & Mesh Hub", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE4E2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Logout", tint = Color(0xFFD92D20))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect & Log Out (@${currentUser?.username})", color = Color(0xFFD92D20), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select App Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("English (US)", "Bengali (বাংলা)", "Español", "Hindi (हिन्दी)", "Français").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLanguage == lang,
                                onClick = {
                                    viewModel.setAppLanguage(lang)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAccountSwitchDialog) {
        MeshNetworkInspectorDialog(
            viewModel = viewModel,
            onDismiss = { showAccountSwitchDialog = false }
        )
    }

    if (showMetaVerifyDialog) {
        AlertDialog(
            onDismissRequest = { showMetaVerifyDialog = false },
            title = { Text("Bluetooth Node Security", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Node: @${currentUser?.username ?: "local"}")
                    Text("Node ID: ${currentUser?.nodeId ?: ""}")
                    Text("All messages and calls use ECDH P-256 + AES-256-GCM end-to-end encryption.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetaVerifyDialog = false }) { Text("OK") }
            }
        )
    }

    if (showReportDialog) {
        var problemDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report a Problem", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("What went wrong with Bluetooth Mesh or the app?")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it },
                        placeholder = { Text("Describe what happened...") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReportDialog = false },
                    enabled = problemDesc.isNotBlank()
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showHelpCenterDialog) {
        AlertDialog(
            onDismissRequest = { showHelpCenterDialog = false },
            title = { Text("Bluetooth Mesh Guide", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Open CPI Social on two or more Android phones and sign in via Bluetooth.", fontWeight = FontWeight.SemiBold)
                    Text("2. Tap 'Discoverable' on one phone and 'Scan Devices' on the other to pair & connect.")
                    Text("3. Once linked, chats, photos, voice notes, and calls travel E2EE over Bluetooth without internet.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpCenterDialog = false }) { Text("Got it") }
            }
        )
    }

    if (showActivityLogDialog) {
        val postsCount by viewModel.feedPosts.collectAsState()
        val callsCount by viewModel.callHistory.collectAsState()
        AlertDialog(
            onDismissRequest = { showActivityLogDialog = false },
            title = { Text("Node Activity Summary", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Local & Synced Posts: ${postsCount.size}")
                    Text("• Recorded Mesh Calls: ${callsCount.size}")
                    Text("• Active Connected Peers: ${viewModel.meshPeers.collectAsState().value.count { it.hopDistance > 0 }}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showActivityLogDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSavedItemsDialog) {
        AlertDialog(
            onDismissRequest = { showSavedItemsDialog = false },
            title = { Text("Local Storage Info", fontWeight = FontWeight.Bold) },
            text = {
                Text("All messages, media, and ECDH identity keys are stored locally on this device.")
            },
            confirmButton = {
                TextButton(onClick = { showSavedItemsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun SettingsRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFF1877F2), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFF1877F2), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

