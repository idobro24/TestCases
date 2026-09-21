package com.example.testcases

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.testcases.ui.CaseListScreen
import com.example.testcases.ui.EditorScreen
import com.example.testcases.ui.ImportScreen
import com.example.testcases.ui.RunScreen
import com.example.testcases.ui.SECTION_ALL
import com.example.testcases.ui.SectionsScreen
import com.example.testcases.ui.TestCaseViewModel
import com.example.testcases.ui.theme.TestCasesTheme
import com.example.testcases.ui.theme.ThemeMode

private val LightScrim = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: TestCaseViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Иконки в статус-баре и панели навигации подстраиваются под выбранную тему
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark }
                )
                onDispose {}
            }

            TestCasesTheme(darkTheme = dark) {
                val nav = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            SectionsScreen(
                                vm = vm,
                                onOpenSection = { nav.navigate("section/$it") },
                                onImport = {
                                    vm.clearImport()
                                    nav.navigate("import")
                                }
                            )
                        }
                        composable(
                            route = "section/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            val sectionId = entry.arguments?.getLong("id") ?: SECTION_ALL
                            CaseListScreen(
                                vm = vm,
                                sectionId = sectionId,
                                onBack = { nav.popBackStack() },
                                onOpen = { nav.navigate("edit/$it") },
                                onCreate = { nav.navigate("edit/0?section=$sectionId") },
                                onRun = { nav.navigate("run/$sectionId") }
                            )
                        }
                        composable(
                            route = "run/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            RunScreen(
                                vm = vm,
                                sectionId = entry.arguments?.getLong("id") ?: SECTION_ALL,
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate("edit/$it") }
                            )
                        }
                        composable(
                            route = "edit/{id}?section={section}",
                            arguments = listOf(
                                navArgument("id") { type = NavType.LongType },
                                navArgument("section") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }
                            )
                        ) { entry ->
                            EditorScreen(
                                vm = vm,
                                id = entry.arguments?.getLong("id") ?: 0L,
                                initialSectionId = entry.arguments?.getLong("section")?.takeIf { it > 0 },
                                onClose = { nav.popBackStack() }
                            )
                        }
                        composable("import") {
                            ImportScreen(
                                vm = vm,
                                onBack = { nav.popBackStack() },
                                onDone = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
