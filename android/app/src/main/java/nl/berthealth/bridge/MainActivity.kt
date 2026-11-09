package nl.berthealth.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.HrvRmssdRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Duration
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
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HrvRmssdRecord::class)
    )
    private lateinit var client: HealthConnectClient
    private lateinit var status: TextView
    private var latestJson = "{}"

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.any { it in permissions }) readHealthData()
        else status.text = "Er is nog geen leesrecht verleend. Tik opnieuw op verbinden en sta minstens één gegevenstype toe."
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
        val connect = Button(this).apply { text = "Verbind / vernieuw gegevens"; setOnClickListener { connectHealth() } }
        val export = Button(this).apply { text = "Exporteer gegevens als JSON"; setOnClickListener {
            if (latestJson == "{}") status.text = "Verbind eerst met Health Connect en vernieuw de gegevens."
            else createFile.launch("bert-health-export.json")
        } }
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
            if (permissions.all { it in granted }) readHealthData() else permissionLauncher.launch(permissions)
        }
    }

    private fun readHealthData() {
        lifecycleScope.launch {
            try {
                status.text = "Gegevens ophalen uit Health Connect…"
                val end = Instant.now()
                val start = end.minus(36, ChronoUnit.HOURS)
                val range = TimeRangeFilter.between(start, end)
                val granted = client.permissionController.getGrantedPermissions()
                val heartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
                val oxygenPermission = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
                val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
                val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
                val hrvPermission = HealthPermission.getReadPermission(HrvRmssdRecord::class)

                val heart = if (heartPermission in granted) {
                    client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range)).records
                        .flatMap { record -> record.samples.map { it.beatsPerMinute.toInt() } }
                } else emptyList()
                val oxygen = if (oxygenPermission in granted) {
                    client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = range)).records
                        .map { it.percentage.value.toDouble() }
                } else emptyList()
                val steps = if (stepsPermission in granted) {
                    client.aggregate(androidx.health.connect.client.request.AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(end.truncatedTo(ChronoUnit.DAYS), end)
                    ))[StepsRecord.COUNT_TOTAL] ?: 0L
                } else null
                val sleepHours = if (sleepPermission in granted) {
                    client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = range)).records
                        .maxByOrNull { it.endTime }?.let {
                            Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
                        }
                } else null
                val hrv = if (hrvPermission in granted) {
                    client.readRecords(ReadRecordsRequest(HrvRmssdRecord::class, timeRangeFilter = range)).records
                        .maxByOrNull { it.time }?.rmssd
                } else null

                val json = JSONObject()
                val metrics = JSONObject()
                if (heart.isNotEmpty()) metrics.put("heartRate", heart.last())
                if (oxygen.isNotEmpty()) metrics.put("spo2", oxygen.last())
                if (steps != null) metrics.put("steps", steps)
                if (sleepHours != null) metrics.put("sleepHours", String.format(java.util.Locale.US, "%.1f", sleepHours).toDouble())
                if (hrv != null) metrics.put("hrv", hrv)
                json.put("metrics", metrics)
                json.put("updatedAt", end.toString())
                json.put("source", "Health Connect")
                latestJson = json.toString(2)

                val missing = mutableListOf<String>()
                if (heartPermission !in granted) missing.add("hartslag")
                if (oxygenPermission !in granted) missing.add("zuurstof")
                if (stepsPermission !in granted) missing.add("stappen")
                if (sleepPermission !in granted) missing.add("slaap")
                if (hrvPermission !in granted) missing.add("HRV")
                status.text = "Bijgewerkt. Hartslagmetingen: ${heart.size}; SpO₂: ${oxygen.size}; stappen vandaag: ${steps ?: "geen toegang"}; slaap: ${sleepHours?.let { String.format(java.util.Locale.US, "%.1f uur", it) } ?: "geen meting"}; HRV: ${hrv?.let { String.format(java.util.Locale.US, "%.1f ms", it) } ?: "geen meting"}. ${if (missing.isEmpty()) "Alle rechten actief." else "Geen toegang tot: ${missing.joinToString(", ")}."} Exporteer daarna het JSON-bestand."
            } catch (e: Exception) {
                status.text = "Lezen mislukt: ${e.message}"
            }
        }
    }
}
