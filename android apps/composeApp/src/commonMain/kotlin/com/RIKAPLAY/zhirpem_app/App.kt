package com.RIKAPLAY.zhirpem_app

import androidx.compose.runtime.*
import com.RIKAPLAY.zhirpem_app.ui.theme.Zhirpem_appTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun App() {
    val settingsManager = remember { SettingsManager() }
    val sessionManager = remember { SessionManager() }
    var showSplash by remember { mutableStateOf(true) }
    var isFirstLaunch by remember { mutableStateOf(settingsManager.isFirstLaunch) }
    
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Zhirpem_appTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSplash) {
                SplashScreen(
                    isEnabled = settingsManager.isSplashScreenEnabled,
                    onNavigateToMain = { showSplash = false }
                )
            } else if (isFirstLaunch) {
                OnboardingScreen(
                    onFinish = { isFirstLaunch = false }
                )
            } else if (!sessionManager.isLoggedIn) {
                AuthScreen(onAuthSuccess = { /* The state is in SessionManager, so just Re-composing works */ })
            } else {
                Scaffold(
                    bottomBar = {
                        val bottomBarItems = listOf(
                            "🏠" to "feed",
                            "🔍" to "search",
                            "💬" to "chats",
                            "👤" to "profile/${sessionManager.username}"
                        )
                        
                        // Only show on main screens
                        if (currentRoute in listOf("feed", "search", "chats", "profile/{username}")) {
                            FluidSwipeBottomBar(
                                isGlassEnabled = settingsManager.isGlassEnabled,
                                glassAlpha = settingsManager.glassAlpha,
                                items = bottomBarItems,
                                selectedLabel = currentRoute ?: "feed",
                                onTabSelected = { route ->
                                    navController.navigate(route) {
                                        popUpTo("feed") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController, 
                        startDestination = "feed",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("feed") {
                            MainFeedScreen(
                                onUserClick = { username -> navController.navigate("profile/$username") },
                                onHashtagClick = {},
                                onMenuClick = { navController.navigate("settings") },
                                onAdminAccess = { navController.navigate("admin") },
                                onShowWhatsNew = {},
                                currentAvatarUrl = null,
                                currentName = sessionManager.name ?: "",
                                showBackupWarning = false,
                                onNavigateToSecurity = { navController.navigate("security") },
                                onDismissBackupWarning = {}
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                onUserClick = { username -> navController.navigate("profile/$username") },
                                onPostClick = { /* Navigate to post details */ }
                            )
                        }
                        composable("chats") {
                            ChatsListScreen(
                                onChatClick = { chatId -> navController.navigate("chat/$chatId") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = { sessionManager.logout() },
                                onNavigateToSecuritySettings = { navController.navigate("security") },
                                onNavigateToOptimization = { navController.navigate("optimization") },
                                currentTheme = AppThemeMode.SYSTEM,
                                onThemeChange = {},
                                onPerformanceModeChanged = {},
                                onFontSizeChanged = {},
                                onGlassModeChanged = {},
                                onGlassAlphaChanged = {}
                            )
                        }
                        composable("profile/{username}") { backStackEntry ->
                            val username = backStackEntry.arguments?.getString("username") ?: ""
                            UserProfileScreen(
                                username = username,
                                onBack = { navController.popBackStack() },
                                onNavigateToChat = { target -> 
                                    // Generate chatId or use a helper
                                    val myU = sessionManager.username ?: ""
                                    val chatId = listOf(myU, target).sorted().joinToString("_")
                                    navController.navigate("chat/$chatId")
                                }
                            )
                        }
                        composable("chat/{chatId}") { backStackEntry ->
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                            ChatScreen(
                                chatId = chatId,
                                onBack = { navController.popBackStack() },
                                onNavigateToPost = {},
                                onOpenCamera = {}
                            )
                        }
                        composable("optimization") {
                            OptimizationScreen(onBack = { navController.popBackStack() })
                        }
                        composable("security") {
                            SecuritySettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("admin") {
                            AdminPanelScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
