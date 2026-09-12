package com.allione.messages

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.allione.messages.model.MessageSource
import com.allione.messages.model.UnifiedMessage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlliOneMessagesApp(onOpenNotificationAccess = {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) }
    }
}

@Composable
private fun AlliOneMessagesApp(onOpenNotificationAccess: () -> Unit) {
    val demo = remember {
        listOf(
            UnifiedMessage("1", "Gabriella", "Kan du köpa mjölk på vägen hem?", System.currentTimeMillis(), MessageSource.WHATSAPP),
            UnifiedMessage("2", "Jonas", "Kommer ni ikväll?", System.currentTimeMillis() - 120000, MessageSource.MESSENGER),
            UnifiedMessage("3", "Hugo", "Träningen slutar 19:30", System.currentTimeMillis() - 240000, MessageSource.SMS),
            UnifiedMessage("4", "Signal kontakt", "Jag skickar adressen här.", System.currentTimeMillis() - 360000, MessageSource.SIGNAL)
        )
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AlliOne Messages", style = MaterialTheme.typography.headlineSmall)
                            Text("Alla dina konversationer på ett ställe", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onOpenNotificationAccess) { Text("Anslut") }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                FilterRow()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(demo) { MessageCard(it) }
                }
            }
        }
    }
}

@Composable
private fun FilterRow() {
    val labels = listOf("Alla", "Messenger", "WhatsApp", "Signal", "SMS")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.take(4).forEachIndexed { index, label ->
            FilterChip(selected = index == 0, onClick = {}, label = { Text(label) })
        }
    }
}

@Composable
private fun MessageCard(message: UnifiedMessage) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 4.dp, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(message.sender.take(1).uppercase()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.sender, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(message.source.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(message.preview, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
