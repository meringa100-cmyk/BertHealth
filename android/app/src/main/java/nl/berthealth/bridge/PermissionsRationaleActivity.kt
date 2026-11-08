package nl.berthealth.bridge

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = TextView(this).apply {
            textSize = 18f
            setPadding(48, 64, 48, 48)
            text = "Bert Health Bridge leest uitsluitend de gezondheidsgegevens waarvoor je toestemming geeft, zoals hartslag, stappen en zuurstofverzadiging. De gegevens worden op dit moment alleen op je telefoon gelezen en op jouw verzoek als JSON-bestand geëxporteerd. Ze worden niet automatisch naar een server verzonden."
        }
        setContentView(message)
    }
}
