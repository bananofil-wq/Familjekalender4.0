package com.allione.messages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.allione.messages.data.MessageStore
import com.allione.messages.model.MessageSource
import com.allione.messages.model.UnifiedMessage
import com.allione.messages.notifications.UnifiedNotificationListener
import com.allione.messages.sms.SmsSupport
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlliOneMessagesApp(
                onOpenNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )
        }
    }
}

@Composable
private fun AlliOneMessagesApp(onOpenNotificationAccess: () -> Unit) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(MessageStore.load(context)) }
    var filter by remember { mutableStateOf<MessageSource?>(null) }
    var selected by remember { mutableStateOf<UnifiedMessage?>(null) }
    var showSmsComposer by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        messages = MessageStore.load(context).sortedByDescending { it.timestamp }
    }

    fun syncSms() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            MessageStore.saveAll(context, SmsSupport.loadInbox(context))
            refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_SMS] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            syncSms()
            status = "SMS är anslutet"
        }
    }

    fun requestMessagingPermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        syncSms()
        while (true) {
            delay(1200)
            refresh()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(onClick = { showSmsComposer = true }) { Text("+") }
            },
            topBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("AlliOne Messages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("SMS • Messenger • WhatsApp • Signal", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenNotificationAccess, modifier = Modifier.weight(1f)) { Text("Anslut appar") }
                            OutlinedButton(onClick = { requestMessagingPermissions() }, modifier = Modifier.weight(1f)) { Text("Aktivera SMS") }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                status?.let {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                FilterRow(filter) { filter = it }
                val visible = if (filter == null) messages else messages.filter { it.source == filter }
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Inga meddelanden ännu. Anslut apparna ovan.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visible, key = { it.id }) { message ->
                            MessageCard(message) { selected = message }
                        }
                    }
                }
            }
        }

        selected?.let { message ->
            ReplyDialog(
                message = message,
                onDismiss = { selected = null },
                onSend = { text ->
                    val ok = if (message.source == MessageSource.SMS) {
                        SmsSupport.send(context, message.sender, text)
                    } else {
                        message.notificationKey?.let { UnifiedNotificationListener.reply(it, text) } == true
                    }
                    if (!ok && message.source != MessageSource.SMS) {
                        val opened = message.notificationKey?.let { UnifiedNotificationListener.open(it) } == true
                        if (!opened) message.packageName?.let { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
                        }
                    }
                    status = if (ok) "Meddelandet skickades" else "Direktsvar saknas, originalappen öppnas när det går"
                    selected = null
                    refresh()
                }
            )
        }

        if (showSmsComposer) {
            SmsComposer(
                onDismiss = { showSmsComposer = false },
                onSend = { number, text ->
                    val ok = SmsSupport.send(context, number, text)
                    status = if (ok) "SMS skickat" else "Ge appen SMS-behörighet först"
                    showSmsComposer = false
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun FilterRow(selected: MessageSource?, onSelected: (MessageSource?) -> Unit) {
    val filters = listOf<Pair<String, MessageSource?>>("Alla" to null, "Messenger" to MessageSource.MESSENGER, "WhatsApp" to MessageSource.WHATSAPP, "Signal" to MessageSource.SIGNAL, "SMS" to MessageSource.SMS)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (label, source) ->
            FilterChip(selected = selected == source, onClick = { onSelected(source) }, label = { Text(label) })
        }
    }
}

@Composable
private fun MessageCard(message: UnifiedMessage, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 4.dp, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(message.sender.take(1).uppercase(), fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.sender, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(sourceLabel(message.source), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(message.preview, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReplyDialog(message: UnifiedMessage, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${message.sender} • ${sourceLabel(message.source)}") },
        text = {
            Column {
                Text(message.preview)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Svar") })
            }
        },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onSend(text) }, enabled = text.isNotBlank()) { Text("Skicka") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

@Composable
private fun SmsComposer(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nytt SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = number, onValueChange = { number = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Telefonnummer") })
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Meddelande") })
            }
        },
        confirmButton = { Button(onClick = { onSend(number, text) }, enabled = number.isNotBlank() && text.isNotBlank()) { Text("Skicka") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

private fun sourceLabel(source: MessageSource) = when (source) {
    MessageSource.SMS -> "SMS"
    MessageSource.MESSENGER -> "Messenger"
    MessageSource.WHATSAPP -> "WhatsApp"
    MessageSource.SIGNAL -> "Signal"
}
