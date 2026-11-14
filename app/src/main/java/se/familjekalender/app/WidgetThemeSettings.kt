package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext

private data class WidgetThemeOption(val value: String, val title: String)

@Composable
internal fun WidgetThemeSettings(calendarTheme: CleanVisualTheme) {
    val context = LocalContext.current
    var activeChoice by remember { mutableStateOf(WidgetThemeColors.savedChoice(context)) }
    var previewChoice by remember { mutableStateOf(activeChoice) }
    var pickerOpen by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }

    // A thumbnail changes the actual Android home-screen widget immediately.
    // Previously this only set previewChoice; the widget stayed unchanged
    // unless users found a separate confirmation at the bottom of the dialog.
    fun applyWidgetChoice(choice: String) {
        WidgetThemeColors.saveChoice(context, choice)
        previewChoice = choice
        activeChoice = choice
        FamilyCalendarWidget.refreshTheme(context)
    }

    // Only seven choices. Existing "Nuvarande" is the protected original widget.
    val options = listOf(
        WidgetThemeOption(WidgetThemeColors.ORIGINAL, "Nuvarande"),
        WidgetThemeOption(CleanVisualTheme.OAK_WOOD.name, "Trä"),
    ) + WidgetThemeColors.premiumLooks.map { WidgetThemeOption(it.id, it.title) }
    fun displayTitle(value: String): String =
        options.firstOrNull { it.value == value }?.title
            ?: if (value == CleanVisualTheme.CURRENT.name) "Nuvarande" else "Nuvarande"

    val featureOptions = remember {
        listOf(
            WidgetFeatureSettings.WEEK_REMINDERS to "Veckans påminnelser",
            WidgetFeatureSettings.LIVE_STATUS to "Pågår / börjar snart",
            WidgetFeatureSettings.BIRTHDAYS to "Födelsedagar inom 7 dagar",
            WidgetFeatureSettings.QUICK_ADD to "Snabbknappen +",
            WidgetFeatureSettings.SYNC_WARNING to "Varning vid synkfel",
            WidgetFeatureSettings.WEEK_NUMBER to "Veckonummer",
        )
    }
    var featureValues by remember {
        mutableStateOf(featureOptions.associate { (key, _) ->
            key to WidgetFeatureSettings.enabled(context, key)
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Widgettema",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Välj mellan sju widgetteman. Kalenderns eget tema påverkas inte.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )
        // Even before the chooser opens, the selected widget is visible in Settings.
        WidgetMaterialPreview(
            choice = activeChoice,
            modifier = Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(17.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayTitle(activeChoice), color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
                Text(
                    "Aktivt widgettema", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = {
                    previewChoice = activeChoice
                    pickerOpen = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB78AFF),
                    contentColor = Color(0xFF1D1529),
                ),
                shape = RoundedCornerShape(13.dp),
            ) { Text("Byt tema", fontWeight = FontWeight.Bold) }
        }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(context, WidgetQuickAddActivity::class.java).apply {
                        putExtra(WidgetQuickAddActivity.EXTRA_PRIVATE_INBOX, true)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
        ) { Text("Mina privata påminnelser") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { detailsExpanded = !detailsExpanded },
        ) {
            Text(if (detailsExpanded) "Dölj widgetinnehåll" else "Anpassa widgetinnehåll")
        }
        if (detailsExpanded) {
            Text("Välj vad som visas i widgeten. Dessa inställningar påverkar inte temat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            featureOptions.forEach { (key, label) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(label, Modifier.weight(1f), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Switch(
                        checked = featureValues[key] ?: true,
                        onCheckedChange = { enabled ->
                            featureValues = featureValues + (key to enabled)
                            WidgetFeatureSettings.setEnabled(context, key, enabled)
                        },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        // Full-screen chooser stays in Settings. Tapping any thumbnail
        // applies the chosen theme without an extra confirmation step.
        Dialog(
            onDismissRequest = { pickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                color = Color(0xFF0D1017),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 19.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TextButton(onClick = { pickerOpen = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Tillbaka",
                                tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Widgettema", fontSize = 23.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Tryck på en bild för att byta tema direkt.",
                                color = Color(0xFFD6D8DF), fontSize = 12.sp)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Surface(
                            color = Color(0xFF1C222B), shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, Color(0xFF414A57)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Valt widgettema", color = Color.White,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Så ser den valda widgetens material och detaljer ut.",
                                    color = Color(0xFFD5D7DE), fontSize = 12.sp,
                                )
                                WidgetMaterialPreview(
                                    choice = previewChoice,
                                    modifier = Modifier.fillMaxWidth().height(460.dp)
                                        .clip(RoundedCornerShape(22.dp)),
                                )
                                Text("Exempeldata visas. Dina egna aktiviteter ändras inte.",
                                    color = Color(0xFFCAD0DA), fontSize = 11.sp)
                            }
                        }
                        Surface(
                            color = Color(0xFF1C222B), shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, Color(0xFF414A57)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Välj tema", color = Color.White, fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold)
                                Text("Tryck på en bild – temat aktiveras direkt.",
                                    color = Color(0xFFD5D7DE), fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                options.chunked(3).forEach { pair ->
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        pair.forEach { option ->
                                            val chosen = previewChoice == option.value ||
                                                (option.value == WidgetThemeColors.ORIGINAL &&
                                                previewChoice == CleanVisualTheme.CURRENT.name)
                                            Column(
                                                modifier = Modifier.weight(1f)
                                                    .clickable { applyWidgetChoice(option.value) },
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Box(Modifier.fillMaxWidth().height(146.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF131720))) {
                                                    WidgetMaterialPreview(
                                                        option.value, Modifier.fillMaxSize())
                                                    if (chosen) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = "Aktivt widgettema",
                                                            tint = Color(0xFFE9C68B),
                                                            modifier = Modifier.align(Alignment.TopEnd)
                                                                .padding(3.dp).size(18.dp),
                                                        )
                                                    }
                                                }
                                                Text(option.title, fontSize = 10.sp,
                                                    color = if (chosen) Color(0xFFFFE1AE)
                                                        else Color(0xFFF1F1F4),
                                                    fontWeight = if (chosen) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                    maxLines = 2,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 5.dp)
                                                        .heightIn(min = 25.dp),
                                                )
                                            }
                                        }
                                        repeat(3 - pair.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF0D1017))
                            .padding(horizontal = 17.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { pickerOpen = false },
                            modifier = Modifier.weight(1f)) {
                            Text("Avbryt", color = Color(0xFFE7C592),
                                fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { pickerOpen = false },
                            modifier = Modifier.weight(2.7f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE7BE83),
                                contentColor = Color(0xFF241B16),
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Klar – temat är aktiverat",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
