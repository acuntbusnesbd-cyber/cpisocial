package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EchoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDarkMode) {
                // Outer scaffold for full safe area screen depth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    AppContent(viewModel)

                    // E2EE Bluetooth Mesh Voice Call & Walkie-Talkie PTT Overlay
                    MeshCallOverlayScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppContent(viewModel: EchoViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    var isSignUpScreen by remember { mutableStateOf(false) }

    if (currentUser == null) {
        if (isSignUpScreen) {
            SignupScreen(
                viewModel = viewModel,
                onSignupSuccess = { isSignUpScreen = false },
                onNavigateToLogin = { isSignUpScreen = false }
            )
        } else {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { /* No-op, automatically triggers StateFlow updates */ },
                onNavigateToSignup = { isSignUpScreen = true }
            )
        }
    } else {
        MainAppNavigationFrame(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigationFrame(viewModel: EchoViewModel) {
    var selectedTab by remember { mutableStateOf("feed") }
    
    // Stack for sub-profiles or chat details navigation
    var viewedProfileIdStack by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isInChatDetail by remember { mutableStateOf(false) }
    var isShowingSettings by remember { mutableStateOf(false) }

    val activeConvId by viewModel.activeConversationId.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()

    // Sync sub-screen chat detail state
    LaunchedEffect(activeConvId) {
        if (activeConvId == null) {
            isInChatDetail = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isInChatDetail && !isShowingSettings && selectedTab != "dms") {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    // Feed Tab
                    NavigationBarItem(
                        selected = selectedTab == "feed" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "feed"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == "feed" && viewedProfileIdStack.isEmpty()) Icons.Rounded.Home else Icons.Outlined.Home,
                                contentDescription = "Home Feed"
                            )
                        },
                        label = { Text("Feed", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_feed")
                    )

                    // Reels Tab (Full screen short videos)
                    NavigationBarItem(
                        selected = selectedTab == "reels" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "reels"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == "reels" && viewedProfileIdStack.isEmpty()) Icons.Rounded.Movie else Icons.Outlined.Movie,
                                contentDescription = "Reels"
                            )
                        },
                        label = { Text("Reels", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_reels")
                    )

                    // Search Tab
                    NavigationBarItem(
                        selected = selectedTab == "search" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "search"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Discover"
                            )
                        },
                        label = { Text("Discover", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_search")
                    )

                    // Create Post Tab
                    NavigationBarItem(
                        selected = selectedTab == "create" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "create"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == "create" && viewedProfileIdStack.isEmpty()) Icons.Rounded.AddCircle else Icons.Outlined.AddCircleOutline,
                                contentDescription = "Create Post"
                            )
                        },
                        label = { Text("Post", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_create")
                    )

                    // Notifications Tab (Activity bell)
                    NavigationBarItem(
                        selected = selectedTab == "notifications" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "notifications"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = if (selectedTab == "notifications" && viewedProfileIdStack.isEmpty()) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = "Notifications"
                                )
                                if (unreadNotifications > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        },
                        label = { Text("Notifications", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_notifications")
                    )

                    // Profile Tab
                    NavigationBarItem(
                        selected = selectedTab == "profile" && viewedProfileIdStack.isEmpty(),
                        onClick = {
                            selectedTab = "profile"
                            viewedProfileIdStack = emptyList()
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == "profile" && viewedProfileIdStack.isEmpty()) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle,
                                contentDescription = "My Profile"
                            )
                        },
                        label = { Text("Profile", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_profile")
                    )

                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen switching logic with stack overlay navigation + BackHandler support
            if (isShowingSettings) {
                BackHandler { isShowingSettings = false }
                FacebookSettingsScreen(
                    viewModel = viewModel,
                    onBackPressed = { isShowingSettings = false }
                )
            } else if (viewedProfileIdStack.isNotEmpty()) {
                BackHandler { viewedProfileIdStack = viewedProfileIdStack.dropLast(1) }
                val currentProfileId = viewedProfileIdStack.last()
                ProfileScreen(
                    viewModel = viewModel,
                    viewedUserId = currentProfileId,
                    onNavigateToChat = {
                        selectedTab = "dms"
                        isInChatDetail = true
                        viewedProfileIdStack = emptyList()
                    },
                    onBackPressed = {
                        viewedProfileIdStack = viewedProfileIdStack.dropLast(1)
                    },
                    onNavigateToSettings = { isShowingSettings = true }
                )
            } else if (isInChatDetail) {
                BackHandler {
                    viewModel.clearActiveConversation()
                    isInChatDetail = false
                }
                DMDetailScreen(
                    viewModel = viewModel,
                    onBackPressed = {
                        viewModel.clearActiveConversation()
                        isInChatDetail = false
                    }
                )
            } else {
                when (selectedTab) {
                    "feed" -> FeedScreen(
                        viewModel = viewModel,
                        onNavigateToProfile = { userId ->
                            viewedProfileIdStack = viewedProfileIdStack + userId
                        },
                        onNavigateToDMs = {
                            selectedTab = "dms"
                        },
                        onNavigateToCreatePost = {
                            selectedTab = "create"
                        },
                        onNavigateToSearch = {
                            selectedTab = "search"
                        },
                        onNavigateToSettings = {
                            isShowingSettings = true
                        }
                    )
                    "reels" -> ReelsScreen(
                        viewModel = viewModel,
                        onNavigateToProfile = { userId ->
                            viewedProfileIdStack = viewedProfileIdStack + userId
                        }
                    )
                    "search" -> SearchScreen(

                        viewModel = viewModel,
                        onNavigateToProfile = { userId ->
                            viewedProfileIdStack = viewedProfileIdStack + userId
                        }
                    )
                    "create" -> CreatePostScreen(
                        viewModel = viewModel,
                        onPostCreated = {
                            selectedTab = "feed"
                        }
                    )
                    "notifications" -> NotificationsScreen(
                        viewModel = viewModel,
                        onNavigateToProfile = { userId ->
                            viewedProfileIdStack = viewedProfileIdStack + userId
                        }
                    )
                    "dms" -> DMsListScreen(
                        viewModel = viewModel,
                        onNavigateToChat = {
                            isInChatDetail = true
                        },
                        onBackPressed = {
                            selectedTab = "feed"
                        }
                    )
                    "profile" -> ProfileScreen(
                        viewModel = viewModel,
                        viewedUserId = viewModel.currentUser.value?.id ?: -1,
                        onNavigateToChat = {},
                        onNavigateToSettings = { isShowingSettings = true }
                    )
                }
            }
        }
    }
}
