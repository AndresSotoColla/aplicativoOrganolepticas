package com.example.aplicativoorganolepticas

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.aplicativoorganolepticas.ui.screens.DataScreen
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aplicativoorganolepticas.ui.screens.HistoryScreen
import com.example.aplicativoorganolepticas.ui.screens.HomeScreen
import com.example.aplicativoorganolepticas.ui.screens.OrganoFormScreen
import com.example.aplicativoorganolepticas.ui.theme.AplicativoOrganolepticasTheme
import com.example.aplicativoorganolepticas.ui.viewmodel.OrganoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AplicativoOrganolepticasTheme {
                val navController = rememberNavController()
                val viewModel: OrganoViewModel = viewModel(factory = OrganoViewModel.Factory)

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            val bloques by viewModel.availableBlocks.collectAsState()
                            val afectaciones by viewModel.afectacionesCatalogo.collectAsState()
                            HomeScreen(
                                onNavigateToForm = { navController.navigate("form") },
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToData = { navController.navigate("data") },
                                onUpdateCatalogs = {
                                    viewModel.actualizarCatalogos { msg ->
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                isUpdatingCatalogs = viewModel.isUpdatingCatalogs,
                                catalogInfo = if (bloques.isEmpty()) "Sin catálogos: actualízalos con conexión antes de registrar"
                                              else "${bloques.size} bloques · ${afectaciones.size} afectaciones en el equipo"
                            )
                        }
                        composable("data") {
                            DataScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("form") {
                            OrganoFormScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onEdit = { r -> viewModel.startEdit(r); navController.navigate("edit") }
                            )
                        }
                        // Edición de un registro no enviado: mismo formulario, en modo edición
                        composable("edit") {
                            OrganoFormScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}