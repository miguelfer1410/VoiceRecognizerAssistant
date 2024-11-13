package com.example.myai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.os.Build
import android.app.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.example.myai.service.VoiceRecognitionService
import com.example.myai.ui.theme.MyAiTheme
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.sdk.android.auth.AuthorizationClient

class MainActivity : ComponentActivity() {
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startVoiceService()
        } else {
            showPermissionDeniedMessage()
        }
    }

    private val CLIENT_ID = "seu_client_id"
    private val REDIRECT_URI = "seu_redirect_uri"
    private val REQUEST_CODE = 1337

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar e solicitar permissões necessárias
        checkAndRequestPermissions()
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        setContent {
            MyAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStartService = { startVoiceService() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            startVoiceService()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startVoiceService() {
        if (Settings.canDrawOverlays(this) &&
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            Intent(this, VoiceRecognitionService::class.java).also { intent ->
                startService(intent)
                showServiceStartedMessage()
            }
        } else {
            showPermissionsRequiredMessage()
        }
    }

    private fun showServiceStartedMessage() {
        Toast.makeText(
            this,
            "Assistente de voz iniciado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPermissionsRequiredMessage() {
        Toast.makeText(
            this,
            "Permissões necessárias não foram concedidas",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "Permissão de áudio negada. O assistente não funcionará.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissão Necessária")
            .setMessage("O acesso ao microfone é necessário para o assistente de voz funcionar. Por favor, habilite nas configurações.")
            .setPositiveButton("Ir para Configurações") { _, _ ->
                // Abre as configurações do app
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissão de Áudio")
            .setMessage("O acesso ao microfone é necessário para que o assistente de voz funcione. Por favor, permita o acesso quando solicitado.")
            .setPositiveButton("Tentar Novamente") { _, _ ->
                requestPermissionLauncher.launch(requiredPermissions)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun authenticateSpotify() {
        val builder = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
        builder.setScopes(arrayOf("streaming"))
        val request = builder.build()
        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, intent)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    val accessToken = response.accessToken
                    // Inicializa o Spotify Player com o accessToken
                    val serviceIntent = Intent(this, VoiceRecognitionService::class.java)
                    serviceIntent.putExtra("SPOTIFY_ACCESS_TOKEN", accessToken)
                    startService(serviceIntent)
                }
                AuthorizationResponse.Type.ERROR -> {
                    // Tratar erro
                }
                else -> {
                    // Tratar outros casos
                }
            }
        }
    }
}

@Composable
fun MainScreen(onStartService: () -> Unit) {
    var showLanguageDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícone do assistente
            Icon(
                painter = painterResource(id = R.drawable.ic_assistant),
                contentDescription = "Assistente de Voz",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Título principal
            Text(
                text = "Assistente de Voz",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Subtítulo/Descrição
            Text(
                text = "Deslize para cima na parte inferior da tela para ativar o assistente",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp)
            )

            // Botão de iniciar
            Button(
                onClick = onStartService,
                modifier = Modifier
                    .height(56.dp)
                    .fillMaxWidth(0.7f),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Iniciar Assistente",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Dicas de uso
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Comandos disponíveis:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CommandItem("Abrir aplicação", "Ex: 'Abrir WhatsApp'")
                    CommandItem("Enviar mensagem", "Ex: 'Enviar mensagem para João'")
                    CommandItem("Parar assistente", "Diga 'Parar'/'Desativar' para desativar")
                }
            }
        }
    }
}


@Composable
private fun CommandItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_bullet),
            contentDescription = null,
            modifier = Modifier
                .size(8.dp)
                .padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}