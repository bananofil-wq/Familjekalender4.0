package se.familjekalender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PreviewScreen()
        }
    }
}

@Composable
private fun PreviewScreen() {
    val palette = paletteFor(ThemeMode.AUTUMN)
    var selectedDate by remember { mutableStateOf(LocalDate.of(2026, 10, 21)) }

    val members = remember {
        listOf(
            SyncMember("a", "Person 1", "", 0xFF45AEFF),
            SyncMember("b", "Person 2", "", 0xFFFF5AA7),
            SyncMember("c", "Person 3", "", 0xFF62DB91)
        )
    }

    val events = remember {
        listOf(
            SyncEvent("1", "Träning", LocalDate.of(2026, 10, 21), "17:00", "a", "manual"),
            SyncEvent("2", "Middag", LocalDate.of(2026, 10, 21), "18:30", "b", "manual"),
            SyncEvent("3", "Aktivitet", LocalDate.of(2026, 10, 21), "19:15", "c", "manual"),
            SyncEvent("4", "", LocalDate.of(2026, 10, 6), "", "a", "manual"),
            SyncEvent("5", "", LocalDate.of(2026, 10, 10), "", "b", "manual"),
            SyncEvent("6", "", LocalDate.of(2026, 10, 14), "", "c", "manual"),
            SyncEvent("7", "", LocalDate.of(2026, 10, 27), "", "a", "manual")
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.accent,
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Bg,
                bottomBar = { PreviewBottomNav(palette.accent) }
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    ExactCalendarScreen(
                        selectedDate = selectedDate,
                        onSelect = { selectedDate = it },
                        events = events,
                        members = members,
                        palette = palette,
                        onAdd = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBottomNav(accent: Color) {
    NavigationBar(containerColor = Color(0xF20F0E13)) {
        val tabs = listOf(
            Icons.Default.CalendarMonth to "Kalender",
            Icons.Default.ShoppingCart to "Inköp",
            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar"
        )
        tabs.forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                selected = index == 0,
                onClick = {},
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.15f)
                )
            )
        }
    }
}
