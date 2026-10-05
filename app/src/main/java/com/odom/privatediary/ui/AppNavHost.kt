package com.odom.privatediary.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.odom.privatediary.download.OnboardingScreen
import com.odom.privatediary.pipeline.PipelineScreen
import com.odom.privatediary.pipeline.PipelineViewModel

@Composable
fun AppNavHost(startDestination: String = "home") {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                vm = viewModel(),
                onReady = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } },
            )
        }
        composable("home") {
            HomeScreen(
                vm = viewModel(),
                onRecord = { nav.navigate("record") },
                onCalendar = { nav.navigate("calendar") },
                onEntry = { id -> nav.navigate("detail/$id") },
            )
        }
        composable("record") {
            val vm: PipelineViewModel = viewModel()
            PipelineScreen(vm)
        }
        composable("calendar") {
            CalendarScreen(
                vm = viewModel(),
                onDayEntry = { id -> nav.navigate("detail/$id") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "detail/{entryId}",
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("entryId").orEmpty()
            DetailScreen(entryId = id, vm = viewModel(), onBack = { nav.popBackStack() })
        }
    }
}
