package com.example.testcases

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.testcases.ui.EditorScreen
import com.example.testcases.ui.ListScreen
import com.example.testcases.ui.TestCaseViewModel
import com.example.testcases.ui.theme.TestCasesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestCasesTheme {
                val nav = rememberNavController()
                val vm: TestCaseViewModel = viewModel()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = nav, startDestination = "list") {
                        composable("list") {
                            ListScreen(
                                vm = vm,
                                onOpen = { nav.navigate("edit/$it") },
                                onCreate = { nav.navigate("edit/0") }
                            )
                        }
                        composable(
                            route = "edit/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            EditorScreen(
                                vm = vm,
                                id = entry.arguments?.getLong("id") ?: 0L,
                                onClose = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
