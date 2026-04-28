package com.example.aplicativoorganolepticas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicativoorganolepticas.ui.theme.DarkBeige
import com.example.aplicativoorganolepticas.ui.theme.LightBeige
import com.example.aplicativoorganolepticas.ui.components.VideoBackground
import com.example.aplicativoorganolepticas.R

@Composable
fun HomeScreen(
    onNavigateToForm: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onExport: () -> Unit,
    onLoadGroups: (Uri) -> Unit
) {
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onLoadGroups(uri)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video de fondo
        VideoBackground(videoResId = R.raw.pineapple_bg)

        // Overlay oscuro para legibilidad
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Evaluación Organoléptica",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Muestreo de Fruta",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            HomeButton(
                text = "Ingresar Registro",
                icon = Icons.Default.Science,
                color = DarkBeige,
                onClick = onNavigateToForm
            )

            Spacer(modifier = Modifier.height(16.dp))

            HomeButton(
                text = "Ver Historial",
                icon = Icons.Default.History,
                color = DarkBeige,
                onClick = onNavigateToHistory
            )

            Spacer(modifier = Modifier.height(16.dp))

            HomeButton(
                text = "Descargar Datos",
                icon = Icons.Default.Download,
                color = DarkBeige,
                onClick = onExport
            )

            Spacer(modifier = Modifier.height(16.dp))

            HomeButton(
                text = "Cargar Grupos",
                icon = Icons.Default.UploadFile,
                color = DarkBeige,
                onClick = {
                    excelLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "application/octet-stream"
                        )
                    )
                }
            )
        }
    }
}

@Composable
fun HomeButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = color,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
