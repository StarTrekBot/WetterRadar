// Datei: app/src/main/java/com/wetterradar/MainActivity.kt

package com.wetterradar

import android.Manifest // Berechtigung nicht mehr benötigt für Standortanzeige
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat // <-- Stelle sicher, dass dies importiert ist
import androidx.core.content.ContextCompat // Für ContextCompat.getDrawable
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
//import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.IconOverlay
import android.graphics.drawable.Drawable
import android.widget.ImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import com.google.android.gms.location.LocationServices
//import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient // Optional, aber gebräuchlich
// NEU: Import für die KTX-Erweiterung, die lastLocation zu suspend-Funktion macht
//import com.google.android.gms.location.awaitLastLocation
//import kotlinx.coroutines.Dispatchers // Für Coroutines
//import kotlinx.coroutines.launch // Für Coroutines
//import kotlinx.coroutines.withContext // Für Coroutines

//class MainActivity : AppCompatActivity() {
class MainActivity : AppCompatActivity(), RadarDownloadManager.OnSingleImageDownloadedCallback {

    private lateinit var mapView: MapView
    private lateinit var statusTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var downloadButton: Button
    // NEU: Füge Variablen für die neuen Buttons hinzu
    private lateinit var previousImageButton: Button
    private lateinit var nextImageButton: Button
    private lateinit var playStopButton: Button

    private lateinit var radarDownloadManager: RadarDownloadManager

    // NEU: Variable für den Standort-IconOverlay (statt Marker)
    private var locationIconOverlay: IconOverlay? = null
    private var currentRadarOverlay: BitmapOverlay? = null
    private var currentRadarBitmap: Bitmap? = null

    private var currentRadarIndex = 0
    private val maxRadarIndex: Int
        get() = RadarDownloadManager.DATASET_SIZE - 1

    private var overlayTransparency: Float = 0.4f

    private var isAnimationRunning = false
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimationRunning) {
                currentRadarIndex = (currentRadarIndex + 1) % (maxRadarIndex + 1)
                loadAndDisplayRadarBitmap(currentRadarIndex)
                animationHandler.postDelayed(this, 1000)  // Time Animation 1sec.
            }
        }
    }

    private var downloadStartTime: Long = 0

    // FÜGE DEN NEUEN ZÄHLER HINZU
    private var downloadedCount = 0
    private var autoPlayAfterLoad = false // Flag für automatischen Start nach Download

    private val radarBoundingBox = BoundingBox(
        55.5533029, 16.0888267, 46.5009905, 4.4286943
    )

    // --- Variablen für gespeicherten Fallback-Standort ---
    private val PREFS_NAME = "LocationPrefs"
    private val PREF_LATITUDE = "latitude"
    private val PREF_LONGITUDE = "longitude"
    private val DEFAULT_LATITUDE = 50.6445
    private val DEFAULT_LONGITUDE = 7.7736
    // -----------------------------------------------------------
    // NEU: Füge eine Referenz zur ImageView der Skala hinzu
    private lateinit var radarScaleImageView: ImageView

    // NEU: Berechtigungen für Standort
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )


    // Toolbar hinzufügen
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_main)

        // Toolbar initialisieren
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name) // Zeigt den App-Namen als Titel an

        mapView = findViewById(R.id.mapView)
        statusTextView = findViewById(R.id.statusTextView)
        timeTextView = findViewById(R.id.timeTextView)
        previousImageButton = findViewById(R.id.previousImageButton)
        nextImageButton = findViewById(R.id.nextImageButton)
        playStopButton = findViewById(R.id.playStopButton)
        downloadButton = findViewById(R.id.downloadButton)

        // NEU: Initialisiere die ImageView
        radarScaleImageView = findViewById(R.id.radarScaleImageView)

        setupMap()

        val radarWidth = 1108
        val radarHeight = 1360
        radarDownloadManager = RadarDownloadManager(this, radarWidth, radarHeight)

        // NEU: Standort-IconOverlay erstellen und hinzufügen
        addLocationIconOverlay()

        setupUI()

        // Karte auf gespeicherten Fallback-Standort oder Standard zentrieren
        centerMapOnStoredLocation()

        startDownloadSequence()
    }

    private fun setupMap() {
        // Konfiguriere die Karte
        mapView.setTileSource(TileSourceFactory.MAPNIK) // OSM Standardkarte
        // Aktueller (aber veralteter) Aufruf
        mapView.setBuiltInZoomControls(true) // <-- Diese Zeile ist korrekt und existiert
        //mapView.setZoomRounding(true)
        mapView.setMultiTouchControls(true)

        // Korrektur: mapView.controller gibt IMapController zurück
        mapView.controller.setZoom(6.0) // Zoomstufe
        mapView.controller.setCenter(GeoPoint(51.5, 10.0)) // Zentrum: Deutschland (ungefähr)
    }

    // --- NEU: Methode zum Erstellen und Hinzufügen des Standort-IconOverlays ---
    private fun addLocationIconOverlay() {
        // Entferne vorheriges Overlay, falls vorhanden
        locationIconOverlay?.let { overlay ->
            mapView.overlays.remove(overlay)
        }

        // Lade den gespeicherten Standort
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLat = sharedPreferences.getFloat(PREF_LATITUDE, DEFAULT_LATITUDE.toFloat()).toDouble()
        val savedLon = sharedPreferences.getFloat(PREF_LONGITUDE, DEFAULT_LONGITUDE.toFloat()).toDouble()

        // Erstelle einen GeoPoint für die Position
        val iconPoint = GeoPoint(savedLat, savedLon)

        // Lade das Icon (z.B. red_dot.xml oder red_dot.png aus res/drawable)
        // Stelle sicher, dass du eine entsprechende Ressource hast
        val iconDrawable: Drawable? = ContextCompat.getDrawable(this, R.drawable.fadenkreuz1_50r) // Passe den Namen an

        // Erstelle das IconOverlay
        // IconOverlay hat einen Konstruktor, der nur GeoPoint und Drawable benötigt
        val newIconOverlay = if (iconDrawable != null) {
            IconOverlay(iconPoint, iconDrawable)
        } else {
            // Fallback: Verwende ein Standard-Icon oder überspringe das Overlay
            Log.w("MainActivity", "Drawable R.drawable.red_dot_test nicht gefunden. Kein Standort-IconOverlay erstellt.")
            return // Verlasse die Funktion, wenn kein Icon verfügbar ist
        }

        // Füge das neue Overlay zur Karte hinzu
        mapView.overlays.add(newIconOverlay)
        locationIconOverlay = newIconOverlay
        mapView.invalidate() // Zeichne die Karte neu
    }
    // -----------------------------------------------------------


    // Methode zum Zentrieren auf gespeicherten Standort
    private fun centerMapOnStoredLocation() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLat = sharedPreferences.getFloat(PREF_LATITUDE, DEFAULT_LATITUDE.toFloat()).toDouble()
        val savedLon = sharedPreferences.getFloat(PREF_LONGITUDE, DEFAULT_LONGITUDE.toFloat()).toDouble()

        if (savedLat == DEFAULT_LATITUDE && savedLon == DEFAULT_LONGITUDE) {
            // Kein gespeicherter Standort, zeige Deutschland
            mapView.controller.setZoom(6.0)
            mapView.controller.setCenter(GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE))
            statusTextView.text = "Kein gespeicherter Standort. Zeige Deutschland."
        } else {
            // Verwende gespeicherten Standort
            mapView.controller.setZoom(12.0)
            mapView.controller.setCenter(GeoPoint(savedLat, savedLon))
            statusTextView.text = "Zeige gespeicherten Standort."
        }
    }


    // Methode zum Speichern des Standorts
    private fun saveLocationToPreferences(lat: Double, lon: Double) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat(PREF_LATITUDE, lat.toFloat())
            putFloat(PREF_LONGITUDE, lon.toFloat())
            apply()
        }
        Log.d("MainActivity", "Standort gespeichert: $lat, $lon")
    }

    // Methode zum Öffnen des Einstellungsdialogs
    private fun openLocationSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_settings, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val buttonGetCurrentLocation: Button = dialogView.findViewById(R.id.buttonGetCurrentLocation)
        val editTextLatitude: EditText = dialogView.findViewById(R.id.editTextLatitude)
        val editTextLongitude: EditText = dialogView.findViewById(R.id.editTextLongitude)
        val buttonSaveLocation: Button = dialogView.findViewById(R.id.buttonSaveLocation)

        // Zeige den aktuellen gespeicherten Standort im Dialog an
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLat = sharedPreferences.getFloat(PREF_LATITUDE, DEFAULT_LATITUDE.toFloat()).toDouble()
        val savedLon = sharedPreferences.getFloat(PREF_LONGITUDE, DEFAULT_LONGITUDE.toFloat()).toDouble()
        if (savedLat != DEFAULT_LATITUDE || savedLon != DEFAULT_LONGITUDE) {
            editTextLatitude.setText(savedLat.toString())
            editTextLongitude.setText(savedLon.toString())
        }

        // NEU: Standortabfrage-Funktionalität wiederherstellen
        buttonGetCurrentLocation.setOnClickListener {
            if (hasLocationPermissions()) {
                // Versuche, den aktuellen Standort zu erhalten
                // Da wir keine MyLocationNewOverlay mehr verwenden, müssen wir den Standort manuell beziehen
                // z.B. über LocationManager oder FusedLocationProviderClient
                // Beispiel mit FusedLocationProviderClient (empfohlen)
                getCurrentLocationUsingFusedProvider { location ->
                    location?.let {
                        val lat = it.latitude
                        val lon = it.longitude
                        editTextLatitude.setText(lat.toString())
                        editTextLongitude.setText(lon.toString())
                        Toast.makeText(this, "Aktueller Standort: $lat, $lon", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(this, "Kein Standort verfügbar. Stellen Sie sicher, dass Standortdienste aktiviert sind.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Fordere Berechtigungen an
                ActivityCompat.requestPermissions(this, locationPermissions, PERMISSION_REQUEST_CODE_LOCATION_DIALOG)
            }
        }

        buttonSaveLocation.setOnClickListener {
            val latStr = editTextLatitude.text.toString()
            val lonStr = editTextLongitude.text.toString()
            try {
                val lat = latStr.toDouble()
                val lon = lonStr.toDouble()
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    saveLocationToPreferences(lat, lon)
                    // Zentriere Karte auf den neuen Standort
                    mapView.controller.setZoom(12.0)
                    mapView.controller.setCenter(GeoPoint(lat, lon))
                    statusTextView.text = "Neuer Standort gespeichert und angezeigt."

                    // --- NEU: Aktualisiere das IconOverlay ---
                    addLocationIconOverlay()
                    // -------------------------------

                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Ungültige Koordinaten!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Bitte gültige Zahlen eingeben!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // NEU: Hilfsmethode zum Prüfen der Standortberechtigungen
    private fun hasLocationPermissions(): Boolean {
        return locationPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

// In MainActivity.kt

    // NEU: Hilfsmethode zum Abrufen des aktuellen Standorts (Beispiel mit FusedLocationProviderClient)
// Diese Methode ist jetzt robust gegenüber fehlenden Berechtigungen.
    private fun getCurrentLocationUsingFusedProvider(onResult: (Location?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Führe den Aufruf nur durch, wenn Berechtigungen vorhanden sind.
        // Optional: Prüfe nochmal explizit hier, falls die Methode an anderer Stelle aufgerufen werden könnte.
        // if (!hasLocationPermissions()) {
        //     Log.w("MainActivity", "getCurrentLocationUsingFusedProvider aufgerufen ohne Berechtigung.")
        //     onResult(null)
        //     return
        // }

        // Versuche, den letzten Standort abzurufen, und fange SecurityException ab.
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location : Location? ->
                    Log.d("MainActivity", "Standort erfolgreich abgerufen über FusedLocationProvider.")
                    onResult(location)
                }
                .addOnFailureListener { exception ->
                    Log.e("MainActivity", "Fehler beim Abrufen des Standorts über FusedLocationProvider (Success/Fail): ${exception.message}", exception)
                    // onFailure kann auch ausgelöst werden, wenn kein Standort verfügbar ist (z.B. GPS noch nicht fixiert)
                    // Gebe null zurück, wenn kein Standort verfügbar ist oder ein Fehler aufgetreten ist.
                    onResult(null)
                }
                // Optional: Falls der Task z.B. vom System abgebrochen wird.
                .addOnCanceledListener {
                    Log.d("MainActivity", "Standortabfrage über FusedLocationProvider abgebrochen.")
                    onResult(null)
                }
        } catch (e: SecurityException) {
            // Dieser Catch-Block fängt den Fehler ab, falls die Berechtigung wider Erwarten fehlt.
            Log.e("MainActivity", "SecurityException bei Standortabfrage: ${e.message}", e)
            // Informiere den Callback, dass kein Standort verfügbar ist.
            onResult(null)
            // Optional: Zeige dem Benutzer eine Meldung an, dass die Berechtigung fehlt.
            // Toast.makeText(this, "Standortzugriff wurde verweigert. Bitte erteilen Sie die Berechtigung.", Toast.LENGTH_LONG).show()
        }
    }

    // NEU: Behandle Ergebnis der Standortberechtigungsanfrage
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE_LOCATION_DIALOG -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("MainActivity", "Standortberechtigungen erteilt.")
                    // Optional: Versuche, direkt nach der Berechtigung den Standort abzufragen
                    // oder lasse den Benutzer den Button erneut drücken.
                    // Hier: Zeige eine kurze Info
                    Toast.makeText(this, "Standortberechtigungen erteilt. Tippe erneut auf 'Standort abfragen'.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("MainActivity", "Standortberechtigungen wurden verweigert.")
                    Toast.makeText(this, "Standortzugriff benötigt, um aktuellen Standort abzurufen.", Toast.LENGTH_LONG).show()
                }
            }
            // Behandle alte Berechtigungen, falls nötig
            // PERMISSION_REQUEST_CODE -> { ... }
        }
    }


    // onCreateOptionsMenu zum Hinzufügen des Menüeintrags
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // onOptionsItemSelected zum Behandeln des Klicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openLocationSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    // NEU: onResume und onPause anpassen (entferne Standort-Overlay-Logik)
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Keine Standort-Overlay-Aktivierung mehr
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopAnimation()
        // Keine Standort-Overlay-Deaktivierung mehr
    }


    // ... (Rest der MainActivity Methoden bleiben weitgehend gleich, aber entferne Standort-Overlay-Logik) ...

    private fun hasPermissions(): Boolean {
        // Alte Berechtigungen nicht mehr relevant
        // Neue Berechtigungen (z.B. für Standortabfrage) werden jetzt im Dialog behandelt
        return true
    }

    // NEU: setupUI Methode anpassen
    private fun setupUI() {
        // Download Button
        downloadButton.setOnClickListener {
            startDownloadSequence()
        }

        // NEU: Previous Image Button
        previousImageButton.setOnClickListener {
            // Berechne den vorherigen Index (mit Wrap-around)
            currentRadarIndex = if (currentRadarIndex == 0) maxRadarIndex else currentRadarIndex - 1
            // Lade und zeige das Bild für den neuen Index
            loadAndDisplayRadarBitmap(currentRadarIndex)
        }

        // NEU: Next Image Button
        nextImageButton.setOnClickListener {
            // Berechne den nächsten Index (mit Wrap-around)
            currentRadarIndex = (currentRadarIndex + 1) % (maxRadarIndex + 1)
            // Lade und zeige das Bild für den neuen Index
            loadAndDisplayRadarBitmap(currentRadarIndex)
        }

        // NEU: Play/Stop Button
        playStopButton.setOnClickListener {
            if (isAnimationRunning) {
                stopAnimation()
            } else {
                startAnimation()
            }
        }
    }

    private fun startDownloadSequence() {
        statusTextView.text = "Lade Radarbilder herunter..."
        timeTextView.text = "--:--"
        downloadStartTime = roundDownloadStartTime(System.currentTimeMillis())

        // SETZE ZÄHLER UND FLAG ZURÜCK
        downloadedCount = 0
        autoPlayAfterLoad = true // Setze Flag, damit nach dem letzten Download gespielt wird
        downloadButton.isEnabled = false // Optional: Deaktiviere Button während Download

        // RUF DIE GEÄNDERTE METHODE AUF UND ÜBERGEBE BEIDE CALLBACKS
        radarDownloadManager.startDownloadSequence(object : RadarDownloadManager.DownloadCallback {
            override fun onProgress(current: Int, total: Int) {
                runOnUiThread {
                    statusTextView.text = "Lade... $current / $total" // Dies zeigt den Fortschritt der Download-Reihe
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    statusTextView.text = "Fehler: $errorMessage"
                    timeTextView.text = "--:--"
                    Log.e("MainActivity", "Download Error: $errorMessage")
                    Toast.makeText(this@MainActivity, "Download fehlgeschlagen: $errorMessage", Toast.LENGTH_LONG).show()
                    downloadButton.isEnabled = true // Reaktiviere Button bei Fehler
                }
            }

            override fun onComplete() {
                runOnUiThread {
                    statusTextView.text = "Download-Reihe abgeschlossen." // Dies wird am Ende der Reihe aufgerufen
                    // Der eigentliche Abschluss (ob alle erfolgreich geladen wurden) wird in onSingleImageDownloaded geprüft
                }
            }
        }, this) // <-- Übergib 'this' als das OnSingleImageDownloadedCallback
    }

    // IMPLEMENTIERE DIE NEUE CALLBACK-METHODE
    override fun onSingleImageDownloaded(index: Int, success: Boolean) {
        runOnUiThread { // Stelle sicher, dass UI-Änderungen im Hauptthread erfolgen
            if (success) {
                downloadedCount++
                statusTextView.text = "Lade Radarbilder herunter... (${downloadedCount}/${maxRadarIndex + 1})"

                // --- WICHTIG: ZEIGE DAS NEU GELADENE BILD AN ---
                // Zeige das Bild nur an, wenn der Play-Modus *nicht* läuft
                if (!isAnimationRunning) {
                    currentRadarIndex = index
                    loadAndDisplayRadarBitmap(currentRadarIndex) // Zeige das neu geladene Bild an
                }

                // --- PRÜFE, OB ALLE BILDER GELADEN SIND ---
                if (index == maxRadarIndex && autoPlayAfterLoad) { // Prüfe, ob das letzte Bild (maxRadarIndex) erfolgreich geladen wurde
                    statusTextView.text = "Alle ${maxRadarIndex + 1} Bilder geladen. Starte Wiedergabe..."
                    autoPlayAfterLoad = false // Zurücksetzen, falls Play gestoppt und erneut gestartet wird
                    startAnimation() // Starte den Play-Modus
                    downloadButton.isEnabled = true // Reaktiviere Button nach Abschluss
                }
            } else {
                // Behandle fehlgeschlagenen Download
                statusTextView.text = "Fehler beim Laden des Bildes für Index $index."
                // Optional: Zähle fehlgeschlagene Downloads nicht mit, oder handle anders
                // In diesem Fall wird autoPlayAfterLoad nicht ausgelöst, wenn das letzte Bild fehlschlägt.
                // Wenn du trotzdem nach Fehlern fortfahren willst, müsstest du die Logik anpassen.
            }
        }
    }


    private fun roundDownloadStartTime(timeMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timeMillis
        }
        val minute = calendar.get(Calendar.MINUTE)
        val roundedMinute = ((minute + 4) / 5) * 5
        if (roundedMinute >= 60) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            calendar.set(Calendar.MINUTE, 0)
        } else {
            calendar.set(Calendar.MINUTE, roundedMinute)
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startAnimation() {
        if (!isAnimationRunning) {
            isAnimationRunning = true
            playStopButton.text = "Stop"
            statusTextView.text = "Animation gestartet."
            animationHandler.post(animationRunnable)
            loadAndDisplayRadarBitmap(currentRadarIndex)
        }
    }

    private fun stopAnimation() {
        if (isAnimationRunning) {
            isAnimationRunning = false
            playStopButton.text = "Start"
            statusTextView.text = "Animation gestoppt."
            animationHandler.removeCallbacks(animationRunnable)
        }
    }

    private fun loadAndDisplayRadarBitmap(index: Int) {
        if (radarDownloadManager.radarCacheFileExists(index)) {
            val bitmap = loadRadarBitmap(index)
            if (bitmap != null) {
                val imageTime = calculateImageTime(index)
                val timeString = formatTimeForDisplay(imageTime)
                timeTextView.text = timeString

                currentRadarOverlay?.let { overlay -> mapView.overlays.remove(overlay) }

                val newRadarOverlay = BitmapOverlay(bitmap, radarBoundingBox, overlayTransparency)
                mapView.overlays.add(newRadarOverlay)
                currentRadarOverlay = newRadarOverlay

                // --- NEU: Stelle sicher, dass das IconOverlay über dem Radar liegt ---
                // Entferne und füge das IconOverlay erneut hinzu, nachdem das Radar-Overlay hinzugefügt wurde
                locationIconOverlay?.let { overlay ->
                    mapView.overlays.remove(overlay)
                    mapView.overlays.add(overlay)
                }
                // --------------------------------------------------------------------------

                mapView.invalidate()
            } else {
                timeTextView.text = "--:--"
                Log.e("MainActivity", "Fehler beim Decodieren des Radars für Index $index")
            }
        } else {
            timeTextView.text = "--:--"
        }
    }

    private fun calculateImageTime(index: Int): Long {
        return downloadStartTime + (index * 5 * 60 * 1000L)
    }

    private fun formatTimeForDisplay(timeMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private fun loadRadarBitmap(index: Int): Bitmap? {
        val file = getRadarFile(index)
        Log.d("MainActivity", "Attempting to load radar bitmap for index $index from: ${file.absolutePath}")

        if (!file.exists()) {
            Log.e("MainActivity", "Radar file does not exist for index $index: ${file.absolutePath}")
            return null
        }

        if (file.length() == 0L) {
            Log.e("MainActivity", "Radar file is empty for index $index: ${file.absolutePath}")
            return null
        }

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                Log.e("MainActivity", "BitmapFactory.decodeFile returned null for index $index. File might be corrupted or not an image: ${file.absolutePath}")
                val header = ByteArray(8)
                file.inputStream().use { it.read(header) }
                Log.d("MainActivity", "File header bytes (index $index): ${header.joinToString(", ") { "0x%02X".format(it) }}")
            } else {
                Log.d("MainActivity", "Successfully loaded bitmap for index $index. Size: ${bitmap.width}x${bitmap.height}")
            }
            bitmap
        } catch (e: OutOfMemoryError) {
            Log.e("MainActivity", "OutOfMemoryError while decoding bitmap for index $index: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e("MainActivity", "Error decoding bitmap for index $index: ${e.message}", e)
            null
        }
    }

    private fun getRadarFile(index: Int): File {
        val cacheDir = File(getExternalFilesDir(null), "radar_cache")
        return File(cacheDir, "radarMN-$index.png")
    }


    override fun onDestroy() {
        super.onDestroy()
        radarDownloadManager.shutdown()
        currentRadarBitmap?.recycle()
        currentRadarBitmap = null
        animationHandler.removeCallbacks(animationRunnable)
        // Standort-IconOverlay sauber entfernen
        locationIconOverlay?.let { overlay ->
            mapView.overlays.remove(overlay)
            // IconOverlay benötigt keine onDetach-Methode wie MyLocationNewOverlay
        }
        locationIconOverlay = null
    }

    companion object {
        // NEU: Request Code für Standortberechtigungsanfrage aus dem Dialog
        private const val PERMISSION_REQUEST_CODE_LOCATION_DIALOG = 102
        // Alte Berechtigungs-Code kann entfernt werden, wenn nicht mehr benötigt
        // private const val PERMISSION_REQUEST_CODE = 100
    }
}