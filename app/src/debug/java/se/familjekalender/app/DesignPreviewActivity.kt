package se.familjekalender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "calendar"
        setContent { ResponsiveApp { PreviewScreen(screen) } }
    }
}

@Composable
private fun PreviewScreen(screen: String) {
    val palette = paletteFor(ThemeMode.AUTUMN)
    var selectedDate by remember { mutableStateOf(LocalDate.of(2026, 10, 21)) }
    val selectedTab = when (screen) {
        "shopping" -> 1
        "todo" -> 2
        "settings" -> 4
        else -> 0
    }

    val members = remember {
        listOf(
            SyncMember("a", "Kim", "Pappa", 0xFF16A8FF),
            SyncMember("b", "Gabriella", "Mamma", 0xFF08DEA0),
            SyncMember("c", "Barn", "Barn", 0xFFFF3D9A),
            SyncMember(ALL_FAMILY_MEMBER_ID, "Hela familjen", "Familj", 0xFFFFD75E)
        )
    }
    val events = remember {
        listOf(
            SyncEvent("1", "Skola", LocalDate.of(2026, 10, 21), "08:00", "a", "manual"),
            SyncEvent("2", "Tandläkare", LocalDate.of(2026, 10, 21), "14:30", "b", "manual"),
            SyncEvent("3", "🌈 Födelsedag", LocalDate.of(2026, 10, 21), "09:00", "c", "manual"),
            SyncEvent("4", "Middag", LocalDate.of(2026, 10, 21), "19:00", ALL_FAMILY_MEMBER_ID, "manual"),
            SyncEvent("5", "", LocalDate.of(2026, 10, 1), "", "a", "manual"),
            SyncEvent("6", "", LocalDate.of(2026, 10, 2), "", ALL_FAMILY_MEMBER_ID, "manual"),
            SyncEvent("7", "🌈 Födelsedag", LocalDate.of(2026, 10, 8), "09:00", "a", "manual")
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.accent,
            secondary = palette.accent,
            surfaceVariant = palette.soft,
            outline = palette.accent.copy(alpha = .55f),
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Scaffold(containerColor = Bg, bottomBar = { PreviewBottomNav(selectedTab, palette.accent) }) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (screen) {
                        "shopping" -> PreviewShopping()
                        "todo" -> PreviewTodo()
                        "settings" -> PreviewSettings()
                        else -> CalendarHomeLayout(assistant = {}, calendar = { ExactCalendarScreen(
                            selectedDate = selectedDate,
                            onSelect = { selectedDate = it },
                            events = events,
                            members = members,
                            palette = palette,
                            onAdd = {}
                        ) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewShopping() {
    var text by remember { mutableStateOf("Mjölk") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Inköpslista", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Synkas mellan era telefoner", color = Muted)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f), singleLine = true)
            PreviewAddButton(enabled = text.isNotBlank())
        }
        Spacer(Modifier.height(12.dp))
        listOf("Bröd", "Kaffe", "Bananer").forEachIndexed { index, name ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(index == 1, {})
                    Text(name, color = if (index == 1) Muted else Color.White)
                }
            }
        }
    }
}

@Composable
private fun PreviewTodo() {
    var text by remember { mutableStateOf("Ring tandläkaren") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("To-Do", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Gemensam lista för familjen", color = Muted)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("Ny uppgift") }, modifier = Modifier.weight(1f), singleLine = true)
            PreviewAddButton(enabled = text.isNotBlank())
        }
        Spacer(Modifier.height(12.dp))
        listOf("Boka besiktning", "Köp present", "Tvätta träningskläder").forEachIndexed { index, name ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(index == 0, {})
                    Text(name, color = if (index == 0) Muted else Color.White)
                }
            }
        }
    }
}

@Composable
private fun PreviewSettings() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Inställningar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Familjekalendern", color = Muted)
        Spacer(Modifier.height(18.dp))
        AppUpdateSettingsCard()
    }
}

@Composable
private fun PreviewAddButton(enabled: Boolean) {
    FilledIconButton(
        onClick = {},
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black.copy(alpha = .78f),
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f),
            disabledContentColor = Color.Black.copy(alpha = .55f)
        )
    ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }
}

@Composable
private fun PreviewBottomNav(selectedTab: Int, accent: Color) {
    NavigationBar(containerColor = Color(0xF20F0E13)) {
        val tabs = listOf(
            Icons.Default.CalendarMonth to "Kalender",
            Icons.Default.ShoppingCart to "Inköp",
            Icons.Default.CheckCircle to "To-Do",
            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar"
        )
        tabs.forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                selected = index == selectedTab,
                onClick = {},
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = .15f),
                    unselectedIconColor = Color(0xFFB8B3D8),
                    unselectedTextColor = Color(0xFFB8B3D8)
                )
            )
        }
    }
}
