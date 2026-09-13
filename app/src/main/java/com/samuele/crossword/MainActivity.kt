package com.samuele.crossword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.ui.editor.EditorScreen
import com.samuele.crossword.ui.editor.EditorViewModel
import com.samuele.crossword.ui.export.ExportScreen
import com.samuele.crossword.ui.navigation.Routes
import com.samuele.crossword.ui.result.ResultScreen
import com.samuele.crossword.ui.result.ResultViewModel
import com.samuele.crossword.ui.theme.CrosswordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CrosswordApp
        setContent {
            CrosswordTheme {
                val nav = rememberNavController()
                var currentResult by remember { mutableStateOf<CrosswordResult?>(null) }

                // ViewModels
                val editorVm: EditorViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return EditorViewModel(app.wordListRepository) as T
                    }
                })
                val resultVm: ResultViewModel = viewModel()

                NavHost(navController = nav, startDestination = Routes.EDITOR) {
                    composable(Routes.EDITOR) {
                        EditorScreen(vm = editorVm) { rows, cols, mask, words ->
                            resultVm.generate(rows, cols, mask, words)
                            nav.navigate(Routes.RESULT)
                        }
                    }
                    composable(Routes.RESULT) {
                        ResultScreen(
                            vm = resultVm,
                            onExport = {
                                currentResult = resultVm.state.value.result
                                nav.navigate(Routes.EXPORT)
                            },
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable(Routes.EXPORT) {
                        val r = currentResult
                        if (r != null) {
                            ExportScreen(result = r, onBack = { nav.popBackStack() })
                        } else {
                            nav.popBackStack()
                        }
                    }
                }
            }
        }
    }
}
