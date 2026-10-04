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
        NavHost(navController = nav, startDestination = "list") {
            composable("list") {
                ContractListScreen(
                    userEmail = signedInUser.email.orEmpty(),
                    onOpen = { id -> nav.navigate("detail/$id") },
                )
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ContractDetailScreen(
                    contractId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenPhoto = { id, page -> nav.navigate("photo/$id/$page") },
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
        }
    }
}
