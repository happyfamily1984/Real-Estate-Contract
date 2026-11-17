package com.happyfamily.contract.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.happyfamily.contract.data.AuthRepository

@Composable
fun AppNavigation() {
    val user by AuthRepository.userFlow.collectAsState(initial = AuthRepository.currentUser)

    val signedInUser = user
    if (signedInUser == null) {
        LoginScreen()
        return
    }

    // 계정이 바뀌면 화면 상태를 새로 만든다.
    key(signedInUser.uid) {
        val nav = rememberNavController()
        val idArg = listOf(navArgument("id") { type = NavType.StringType })
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    userEmail = signedInUser.email.orEmpty(),
                    onOpenFolder = { id -> nav.navigate("folder/$id") },
                    onOpenContract = { id -> nav.navigate("detail/$id") },
                    onOpenStakeholders = { nav.navigate("stakeholders") },
                    onOpenAiSettings = { nav.navigate("ai-settings") },
                )
            }
            composable("ai-settings") { AiSettingsScreen(onBack = { nav.popBackStack() }) }
            composable("folder/{id}", arguments = idArg) { entry ->
                FolderScreen(
                    folderId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenContract = { id -> nav.navigate("detail/$id") },
                )
            }
            composable("detail/{id}", arguments = idArg) { entry ->
                ContractDetailScreen(
                    contractId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenPhoto = { id, page -> nav.navigate("photo/$id/$page") },
                    onOpenFolder = { id ->
                        // 폴더에서 들어왔으면 그대로 돌아가고, 아니면 폴더를 연다.
                        val previous = nav.previousBackStackEntry
                        if (previous?.destination?.route == "folder/{id}" && previous.arguments?.getString("id") == id) {
                            nav.popBackStack()
                        } else {
                            nav.navigate("folder/$id")
                        }
                    },
                )
            }
            composable(
                "photo/{id}/{page}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType },
                ),
            ) { entry ->
                PhotoViewerScreen(
                    contractId = entry.arguments?.getString("id").orEmpty(),
                    initialPage = entry.arguments?.getInt("page") ?: 0,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("stakeholders") {
                StakeholdersScreen(
                    onBack = { nav.popBackStack() },
                    onOpenReport = { id -> nav.navigate("report/$id") },
                )
            }
            composable("report/{id}", arguments = idArg) { entry ->
                StakeholderReportScreen(
                    stakeholderId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
