package nl.berthealth.bridge

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject
import java.io.OutputStreamWriter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button

class MainActivity : ComponentActivity() {
    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    )
    private lateinit var client: HealthConnectClient
    private lateinit var status: TextView
    private var latestJson = "{}"

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) readHealthData()
        else status.text = "Niet alle leesrechten zijn verleend. Je kunt dit later opnieuw proberen."
    }

    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out).use { it.write(latestJson) }
            status.text = "JSON-export opgeslagen. Je kunt dit bestand importeren in Bert Health Live."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 36) }
        val title = TextView(this).apply { text = "Bert Health Bridge"; textSize = 26f }
        status = TextView(this).apply { text = "Klaar om Health Connect te controleren."; textSize = 16f; setPadding(0, 28, 0, 28) }
        val connect = Button(this).apply { text = "Verbind met Health Connect"; setOnClickListener { connectHealth() } }
        val export = Button(this).apply { text = "Exporteer gegevens als JSON"; setOnClickListener { createFile.launch("bert-health-export.json") } }
        layout.addView(title); layout.addView(status); layout.addView(connect); layout.addView(export)
        setContentView(layout)
    }

    private fun connectHealth() {
        val availability = HealthConnectClient.getSdkStatus(this)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            status.text = "Health Connect is niet beschikbaar of moet worden bijgewerkt."
            return
        }
        client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) readHealthData() else permissionLauncher.launch(permissions)
        }
    }

    private fun readHealthData() {
        lifecycleScope.launch {
            try {
                val end = Instant.now()
                val start = end.minus(24, ChronoUnit.HOURS)
                val range = TimeRangeFilter.between(start, end)
                val heart = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range)).records
                    .flatMap { record -> record.samples.map { it.beatsPerMinute.toInt() } }
                val oxygen = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = range)).records
                    .map { it.percentage.value.toDouble() }
                val steps = client.aggregate(androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start.truncatedTo(ChronoUnit.DAYS), end)
                ))[StepsRecord.COUNT_TOTAL] ?: 0L
                val json = JSONObject()
                val metrics = JSONObject()
                if (heart.isNotEmpty()) metrics.put("heartRate", heart.last())
                if (oxygen.isNotEmpty()) metrics.put("spo2", oxygen.last())
                metrics.put("steps", steps)
                json.put("metrics", metrics)
                json.put("updatedAt", end.toString())
                json.put("source", "Health Connect")
                latestJson = json.toString(2)
                status.text = "Gegevens gelezen. Hartslagrecords: ${heart.size}; SpO₂-records: ${oxygen.size}; stappen vandaag: ${steps}. Kies export om JSON op te slaan."
            } catch (e: Exception) {
                status.text = "Lezen mislukt: ${e.message}"
            }
        }
    }
}
