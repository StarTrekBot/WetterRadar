// Datei: app/src/main/java/com/wetterradar/RadarDownloadManager.kt

package com.wetterradar

import android.content.Context
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RadarDownloadManager(context: Context, private val fixedWidth: Int, private val fixedHeight: Int) {

    private val context: Context = context.applicationContext // Verwende ApplicationContext
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4) // Anzahl der parallelen Threads

    companion object {
        private const val TAG = "RadarDownloadManager"
        private const val TIMESTEP_5MINUTES: Long = 5 * 60 * 1000L
        // Zentrale Konstante: Anzahl der zu ladenden Datensätze (0 bis DATASET_SIZE - 1)
        // Setze diesen Wert auf die gewünschte Anzahl (z.B. 16 für 80 Minuten Zukunft)
        const val DATASET_SIZE = 16  //für die Anzahl der zu ladenden Bilder

        // WMS-URL-Basis für das Radarprodukt WN (angepasst aus dem Originalcode)
        // Hinweis: styles=& wurde entfernt, wie im Originalcode zu sehen war
        private const val WN_RADAR_URL_TEMPLATE =
            "//maps.dwd.de/geoserver/dwd/wms?" +
                    "service=WMS&" +
                    "version=1.1.0&" +
                    "request=GetMap&" +
                    "layers=dwd:Radar_wn-product_1x1km_ger&" + // Direktes Doppelpunkt-Zeichen
                    "bbox=493000.00%2C5861000.00%2C1791000.00%2C7470000.00&" + // %%2C = URL-escaped ","
                    "time=TIMESTAMP&" + // Dieser Teil wird ersetzt
                    "width=WIDTH_PLACEHOLDER&" + // Dieser Teil wird ersetzt
                    "height=HEIGHT_PLACEHOLDER&" + // Dieser Teil wird ersetzt
                    "srs=EPSG%3A3857&" + // %%3A = URL-escaped ":"
                    "format=image%2Fpng" // %%2F = URL-escaped "/"

        private const val RADAR_CACHE_FILENAME_PREFIX = "radarMN-"
        private const val RADAR_CACHE_FILENAME_SUFFIX = ".png"
    }


    // Hilfsmethode zum Erstellen der vollständigen WMS-URL für eine bestimmte Zeit
    private fun getUrlForTime(timeMillis: Long, useHttps: Boolean): URL {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeString = sdf.format(Date(timeMillis))

        // Baue die URL dynamisch, ersetze Platzhalter
        var baseUrl = WN_RADAR_URL_TEMPLATE
            .replace("TIMESTAMP", timeString)
            .replace("WIDTH_PLACEHOLDER", fixedWidth.toString())
            .replace("HEIGHT_PLACEHOLDER", fixedHeight.toString())

        val fullUrlString = (if (useHttps) "https:" else "http:") + baseUrl
        Log.d(TAG, "Generated URL: $fullUrlString") // Debug-Log

        return URL(fullUrlString)
    }

    // Methode zum Herunterladen des Radarbilds als InputStream für eine bestimmte Zeit
    @Throws(IOException::class)
    private fun getRadarInputStream(timeMillis: Long): InputStream {
        val url = getUrlForTime(timeMillis, true) // Verwende HTTPS
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        // Optional: Header setzen, z.B. User-Agent
        connection.setRequestProperty("User-Agent", "WetterRadarApp/1.0")

        val responseCode = connection.responseCode
        val contentType = connection.contentType // Logge den Content-Type
        Log.d(TAG, "Response Code for ${getUrlForTime(timeMillis, true)}: $responseCode, Content-Type: $contentType")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            return connection.inputStream
        } else {
            Log.e(TAG, "HTTP Error $responseCode for URL: $url")
            // Versuche, den Inhalt der Fehlerantwort zu lesen und zu loggen
            val errorStream = connection.errorStream
            if (errorStream != null) {
                try {
                    errorStream.use { stream ->
                        val errorContent = stream.bufferedReader().use { reader -> reader.readText() }
                        Log.e(TAG, "Error response body (first 500 chars): ${errorContent.take(500)}")
                        // Optional: Speichere den Fehlerinhalt zur Analyse
                        // saveErrorResponseBody(errorContent, timeMillis)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Could not read error stream: ${e.message}", e)
                }
            }
            throw IOException("HTTP Error $responseCode")
        }
    }

    // Optional: Methode zum Speichern der Fehlerantwort für Debugging-Zwecke
    // private fun saveErrorResponseBody(content: String, timeMillis: Long) {
    //     val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
    //     sdf.timeZone = TimeZone.getTimeZone("UTC")
    //     val timeString = sdf.format(Date(timeMillis))
    //     val file = File(context.getExternalFilesDir(null), "radar_cache/error_$timeString.xml")
    //     file.writeText(content)
    //     Log.d(TAG, "Saved error response to: ${file.absolutePath}")
    // }

    // Methode zum Abrufen der Cache-Datei für einen bestimmten Index
    private fun getRadarFile(index: Int): File {
        val cacheDir = File(context.getExternalFilesDir(null), "radar_cache").apply {
            if (!exists()) mkdirs()
        }
        return File(cacheDir, "${RADAR_CACHE_FILENAME_PREFIX}${index}${RADAR_CACHE_FILENAME_SUFFIX}")
    }

    // Methode zum Prüfen, ob eine Cache-Datei existiert
    fun radarCacheFileExists(index: Int): Boolean {
        val file = getRadarFile(index)
        return file.exists() && file.length() > 0L // Prüfe auch auf Länge > 0
    }

    // Methode zum Speichern des InputStreams im Cache
    private fun putRadarMapToCache(inputStream: InputStream, index: Int): Boolean {
        val file = getRadarFile(index)
        return try {
            file.outputStream().use { fileOutputStream ->
                inputStream.use { bufferedInputStream ->
                    val cache = ByteArray(1024)
                    var bytesRead: Int
                    var filesize: Long = 0
                    while (bufferedInputStream.read(cache).also { bytesRead = it } != -1) {
                        fileOutputStream.write(cache, 0, bytesRead)
                        filesize += bytesRead
                    }
                }
            }
            //Log.d(TAG, "Saved radar image to cache: ${file.absolutePath}, size: $filesize")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving radar image to cache: ${e.message}", e)
            false
        }
    }

    // Methode zum Herunterladen einer Reihe von Radarbildern (z.B. von start bis stop)
    // Ändere die fetchRadarSet Methode, sodass sie das neue Callback akzeptiert
    // ÄNDERE DIE fetchRadarSet METHODE, UM DAS NEUE CALLBACK ZU NUTZEN
    // Die Methode bekommt jetzt ein zusätzliches Argument: singleCallback
    private fun fetchRadarSet(start: Int, stop: Int, callback: DownloadCallback?, singleCallback: OnSingleImageDownloadedCallback?) {
        for (i in start..stop) {
            val targetTime = roundUTCUpToNextFiveMinutes(System.currentTimeMillis()) + i * TIMESTEP_5MINUTES
            Log.d(TAG, "Fetching radar image for index $i at time: ${Date(targetTime)}")
            try {
                getRadarInputStream(targetTime).use { inputStream ->
                    val success = putRadarMapToCache(inputStream, i)
                    if (success) {
                        Log.d(TAG, "Successfully downloaded and cached radar image $i")
                        callback?.onProgress(i, stop - start + 1) // Fortschritt melden
                        // NEU: Melde das einzelne Bild als erfolgreich geladen
                        singleCallback?.onSingleImageDownloaded(i, true)
                    } else {
                        Log.e(TAG, "Failed to cache radar image $i")
                        callback?.onError("Failed to cache image $i")
                        // NEU: Melde das einzelne Bild als fehlgeschlagen
                        singleCallback?.onSingleImageDownloaded(i, false)
                        // Optional: Abbrechen, wenn ein Bild fehlschlägt
                        // return
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to download radar image $i: ${e.message}", e)
                callback?.onError("Download failed for image $i: ${e.message}")
                // NEU: Melde das einzelne Bild als fehlgeschlagen
                singleCallback?.onSingleImageDownloaded(i, false)
                // Optional: Abbrechen, wenn ein Bild fehlschlägt
                // return
            }
        }
        callback?.onComplete()
    }

    // Hilfsmethode zum Runden der Zeit auf die nächste 5-Minuten-Grenze (UTC)
    private fun roundUTCUpToNextFiveMinutes(timeMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timeMillis
        }
        val minute = calendar.get(Calendar.MINUTE)
        val roundedMinute = ((minute + 4) / 5) * 5 // Runde auf nächste 5er Minute auf
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

    // ÄNDERE DIE startDownloadSequence METHODE, DAMIT SIE DAS NEUE CALLBACK AKZEPTIERT
    // Füge ein neues Argument hinzu, standardmäßig null für Abwärtskompatibilität
    fun startDownloadSequence(callback: DownloadCallback, singleCallback: OnSingleImageDownloadedCallback? = null) {
        // Optional: Lösche vorherige Cache-Dateien, wenn eine neue Sequenz gestartet wird
        deleteCacheFiles()

        // Berechne Startzeit (aktuelle Zeit, auf nächste 5-Minuten-Grenze gerundet)
        val startTime = roundUTCUpToNextFiveMinutes(System.currentTimeMillis())
        Log.d(TAG, "Starting download sequence from time: ${Date(startTime)}")

        // Starte Download-Reihe im Hintergrund-Thread
        executorService.execute {
            fetchRadarSet(0, DATASET_SIZE - 1, callback, singleCallback) // Rufe fetchRadarSet mit beiden Callbacks auf
        }
    }

    // Optional: Neue Methode, falls du nur das Single-Callback verwenden willst
    fun startDownloadSequenceWithSingleCallback(singleCallback: OnSingleImageDownloadedCallback, progressCallback: DownloadCallback? = null) {
        executorService.execute {
            fetchRadarSet(0, DATASET_SIZE - 1, progressCallback, singleCallback)
        }
    }

    // Optional: Methode zum Löschen aller Cache-Dateien
    fun deleteCacheFiles() {
        val cacheDir = File(context.getExternalFilesDir(null), "radar_cache")
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(RADAR_CACHE_FILENAME_PREFIX) && file.name.endsWith(RADAR_CACHE_FILENAME_SUFFIX)) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted cached radar file: ${file.name}")
                    } else {
                        Log.w(TAG, "Could not delete cached radar file: ${file.name}")
                    }
                }
            }
        }
    }

    // Interface für Callbacks (zurück zur MainActivity)
    interface DownloadCallback {
        fun onProgress(current: Int, total: Int)
        fun onError(errorMessage: String)
        fun onComplete()
    }

    // Füge das neue Interface direkt unterhalb des alten hinzu
    interface OnSingleImageDownloadedCallback {
        fun onSingleImageDownloaded(index: Int, success: Boolean)
    }

    // Methode zum Herunterfahren des Thread-Pools (z.B. in onDestroy() der Activity)
    fun shutdown() {
        if (!executorService.isShutdown) {
            executorService.shutdown()
        }
    }
}
