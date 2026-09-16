package se.familjekalender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Health Connect", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Familjekalendern använder Health Connect för att läsa dina löppass och den distans som hör till passen."
                        )
                        Text(
                            "När du väljer att ansluta importeras en sammanfattning av löppasset till familjekalendern för den person som är vald på den här telefonen. Sammanfattningen används för löpstatistik, progression och historik."
                        )
                        Text(
                            "Appen begär inte rätt att skriva eller ändra data i Health Connect. Om du godkänner bakgrundsåtkomst kan nya löppass synkas automatiskt."
                        )
                        Text(
                            "Du kan när som helst ändra eller återkalla Health Connect-behörigheterna i Androids Health Connect-inställningar."
                        )
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stäng")
                        }
                    }
                }
            }
        }
    }
}
