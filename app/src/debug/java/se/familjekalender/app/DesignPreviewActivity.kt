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
        setContent { PreviewScreen() }
    }
}

@Composable
private fun PreviewScreen() {
    val palette = paletteFor(ThemeMode.AUTUMN)
    var selectedDate by remember { mutableStateOf(LocalDate.of(2026, 10, 21)) }

    val members = remember {
        listOf(
            SyncMember("a", "Person 1", "", 0xFF16A8FF),
            SyncMember("b", "Person 2", "", 0xFF08DEA0),
            SyncMember("c", "Person 3", "", 0xFFFF3D9A),
            SyncMember("d", "Familjen", "", 0xFFFF9000)
        )
    }

    val events = remember {
        listOf(
            SyncEvent("1", "Skola", LocalDate.of(2026, 10, 21), "08:00", "a", "manual"),
            SyncEvent("2", "Tandläkare", LocalDate.of(2026, 10, 21), "14:30", "b", "manual"),
            SyncEvent("3", "Fotboll", LocalDate.of(2026, 10, 21), "16:30", "c", "manual"),
            SyncEvent("4", "Middag", LocalDate.of(2026, 10, 21), "19:00", "d", "manual"),
            SyncEvent("5", "", LocalDate.of(2026, 10, 1), "", "a", "manual"),
            SyncEvent("6", "", LocalDate.of(2026, 10, 2), "", "d", "manual"),
            SyncEvent("7", "", LocalDate.of(2026, 10, 8), "", "a", "manual"),
            SyncEvent("8", "", LocalDate.of(2026, 10, 10), "", "d", "manual"),
            SyncEvent("9", "", LocalDate.of(2026, 10, 12), "", "c", "manual"),
            SyncEvent("10", "", LocalDate.of(2026, 10, 13), "", "a", "manual"),
            SyncEvent("11", "", LocalDate.of(2026, 10, 14), "", "b", "manual"),
            SyncEvent("12", "", LocalDate.of(2026, 10, 15), "", "d", "manual"),
            SyncEvent("13", "", LocalDate.of(2026, 10, 19), "", "d", "manual"),
            SyncEvent("14", "", LocalDate.of(2026, 10, 23), "", "d", "manual"),
            SyncEvent("15", "", LocalDate.of(2026, 10, 24), "", "d", "manual"),
            SyncEvent("16", "", LocalDate.of(2026, 10, 28), "", "b", "manual"),
            SyncEvent("17", "", LocalDate.of(2026, 10, 31), "", "d", "manual")
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9C4DFF),
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Bg,
                bottomBar = { PreviewBottomNav() }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
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
private fun PreviewBottomNav() {
    val purple = Color(0xFF9C4DFF)
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
                    selectedIconColor = purple,
                    selectedTextColor = purple,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB8B3D8),
                    unselectedTextColor = Color(0xFFB8B3D8)
                )
            )
        }
    }
}
