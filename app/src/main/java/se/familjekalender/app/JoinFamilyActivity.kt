package se.familjekalender.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class JoinFamilyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")?.trim()?.uppercase().orEmpty()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB47CFF),
                    background = Color(0xFF0F0E13),
                    surface = Color(0xFF1B191F),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0F0E13)) {
                    JoinFamilyScreen(
                        code = code,
                        onJoined = { session ->
                            getSharedPreferences("family_calendar", 0).edit()
                                .putString("family_id", session.id)
                                .putString("family_name", session.name)
                                .putString("family_code", session.code)
                                .apply()
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinFamilyScreen(
    code: String,
    onJoined: (FamilySession) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💜", fontSize = 40.sp)
                Spacer(Modifier.height(10.dp))
                Text("Gå med i familjen?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (code.isBlank()) "Inbjudningslänken saknar familjekod." else "Du har fått en inbjudan till Familjekalendern.",
                    color = Color(0xFFAAA4B2)
                )
                if (code.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Familjekod", color = Color(0xFFAAA4B2), fontSize = 12.sp)
                    Text(code, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            runCatching { SupabaseSync.joinFamily(code) }
                                .onSuccess(onJoined)
                                .onFailure { error = it.message ?: "Kunde inte gå med i familjen" }
                            busy = false
                        }
                    },
                    enabled = !busy && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Ansluter…" else "Gå med i familjen") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Inte nu")
                }
            }
        }
    }
}
