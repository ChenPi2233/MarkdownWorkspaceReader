package com.example.mdworkspace.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mdworkspace.MdWorkspaceApplication
import com.example.mdworkspace.ui.reader.ReaderScreen
import com.example.mdworkspace.ui.setup.SetupScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as MdWorkspaceApplication
    val config by app.container.repository.configFlow.collectAsStateWithLifecycle(
        initialValue = com.example.mdworkspace.domain.model.RepositoryConfig()
    )
    val readerSession by app.container.sessionStore.readerSessionFlow.collectAsStateWithLifecycle(
        initialValue = com.example.mdworkspace.core.datastore.ReaderSessionState()
    )
    var triedRestoreLastDocument by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Routes.reader()
    ) {
        composable(
            route = Routes.Setup,
            arguments = listOf(navArgument("edit") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) { backStackEntry ->
            val edit = backStackEntry.arguments?.getBoolean("edit") ?: false
            LaunchedEffect(config.isComplete) {
                if (config.isComplete && !edit) {
                    navController.navigate(Routes.reader()) {
                        popUpTo(Routes.Setup) { inclusive = true }
                    }
                }
            }
            SetupScreen(
                onConnected = {
                    navController.navigate(Routes.reader()) {
                        popUpTo(Routes.Setup) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.Reader,
            arguments = listOf(navArgument("path") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            })
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path")?.let(Uri::decode).orEmpty()
            LaunchedEffect(path, readerSession.lastDocumentPath) {
                if (!triedRestoreLastDocument && path.isBlank() && readerSession.lastDocumentPath.isNotBlank()) {
                    triedRestoreLastDocument = true
                    navController.navigate(Routes.reader(readerSession.lastDocumentPath)) {
                        launchSingleTop = true
                        popUpTo(Routes.Reader) { inclusive = true }
                    }
                }
            }
            ReaderScreen(
                path = path,
                onBack = {
                    if (path.isNotBlank()) navController.popBackStack()
                },
                onOpenSettings = {
                    navController.navigate(Routes.setup(edit = true))
                },
                onOpenDocument = {
                    navController.navigate(Routes.reader(it)) {
                        launchSingleTop = true
                        popUpTo(Routes.Reader) { inclusive = true }
                    }
                }
            )
        }
    }
}
