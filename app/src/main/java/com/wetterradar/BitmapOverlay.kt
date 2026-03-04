// Datei: app/src/main/java/com/wetterradar/BitmapOverlay.kt

package com.wetterradar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color // <- Hinzufügen
import android.graphics.ColorFilter // <- Hinzufügen
import android.graphics.Paint
import android.graphics.PorterDuff // <- Hinzufügen
import android.graphics.PorterDuffColorFilter // <- Hinzufügen
import android.graphics.Rect
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/**
 * Ein benutzerdefiniertes Overlay, das ein gegebenes Bitmap in einer definierten
 * geografischen Bounding Box auf der osmdroid-Karte anzeigt.
 */
/**
 * Ein benutzerdefiniertes Overlay, das ein gegebenes Bitmap in einer definierten
 * geografischen Bounding Box auf der osmdroid-Karte anzeigt.
 * @param bitmap Das anzuzeigende Bitmap. Seine eigene Alpha-Information (Transparenz) wird beibehalten.
 * @param boundingBox Die geografische Bounding Box.
 * @param overallTransparency Maximale Transparenz für das gesamte Overlay (0.0 = vollständig transparent, 1.0 = vollständig sichtbar).
 *                           Die eigene Transparenz des Bitmaps bleibt erhalten und kann nur *unter* diesen Wert gehen.
 * @param colorTint Optionaler Farbwert zum Tönen des Overlays (z.B. Color.RED). Standard ist Color.WHITE (keine Tönung).
 */
class BitmapOverlay(
    private val bitmap: Bitmap,
    private val boundingBox: BoundingBox,
    private val overallTransparency: Float = 0.4f, // <-- Umbenannt und Standardwert angepasst
    private val colorTint: Int = Color.WHITE // Standard: Keine Tönung
) : Overlay() {

    private val paint = Paint().apply {
        // Setze den Porter-Duff-Modus, um die Alpha des Bitmaps zu erhalten
        // DST_IN blendet nur den Anteil des Quellbildes (Bitmap) ein, der mit dem Ziel (Karte) übereinstimmt.
        // Dadurch behält das Bitmap seine Alpha-Werte bei.
        // Der xfermode beeinflusst, *wie* die Alpha angewendet wird.
        // xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) // Dieser Modus ist NICHT korrekt für das gewünschte Verhalten.

        // Korrekter Ansatz: Verwende PorterDuff.Mode.DST_OVER oder DST_ATOP.
        // DST_OVER zeichnet das Quellbild (Bitmap) über das Ziel (Karte), unter Berücksichtigung der Alpha des Quellbildes.
        // Dann wenden wir unsere 'overallTransparency' als Alpha-Faktor an.
        // Der beste Weg, die Bitmap-Alpha zu erhalten UND gleichzeitig eine Max-Transparenz zu setzen,
        // ist, den Alpha-Wert des Paint-Objekts zu verwenden, aber sicherzustellen, dass er
        // nicht die bereits vorhandene Alpha des Bitmaps *überschreibt*, sondern *multipliziert*.
        // paint.setAlpha((overallTransparency * 255).toInt().coerceIn(0, 255))
        // und der Standard-Modus (PorterDuff.Mode.SRC_OVER) macht das meiste richtig.
        // Um die *eigene* Alpha des Bitmaps zu *erhalten*, ist der Trick, den Modus NICHT zu ändern
        // und paint.setAlpha() als *multiplikativer Faktor* zu verwenden.
        // Die Standard-Paint-Einstellung ist SRC_OVER.
        // Um sicherzustellen, dass die Bitmap-Alpha *immer* erhalten bleibt und nur *maximal* overallTransparency sichtbar ist,
        // kann man den Modus DST_IN verwenden, aber VORHER eine "Maske" mit der gewünschten Transparenz erstellen.
        // Einfacher ist es, paint.setAlpha() als Multiplikator zu verwenden, was der Standard ist.

        // Korrektur: Der Standard-Modus SRC_OVER ist meistens der gewünschte.
        // Die Bitmap wird mit ihrer eigenen Alpha gezeichnet.
        // paint.alpha wird als zusätzlicher Faktor angewendet.
        // Also: Endgültige Alpha = Bitmap_Alpha * (overallTransparency * 255)
        // Dadurch bleibt z.B. ein Bitmap-Pixel mit Alpha 0 (transparent) transparent,
        // und ein Pixel mit Alpha 200 wird zu 200 * 0.9 = 180 (oder entsprechend gerundet).
        // paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) // Nicht notwendig, SRC_OVER ist Standard
        alpha = (overallTransparency * 255).toInt().coerceIn(0, 255) // Setze Alpha-Wert als Multiplikator
        if (colorTint != Color.WHITE) {
            // Füge einen ColorFilter hinzu, wenn eine Tönung gewünscht ist
            colorFilter = PorterDuffColorFilter(colorTint, PorterDuff.Mode.MULTIPLY) // Oder ein anderer geeigneter Modus
        }
        isFilterBitmap = true // Aktiviert Filterung für bessere Skalierung
        isAntiAlias = true // Aktiviert Kantenglättung
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return // Ignoriere Schattenmodus

        val projection: Projection = mapView.projection
        val nwPoint = projection.toPixels(GeoPoint(boundingBox.latNorth, boundingBox.lonWest), null)
        val sePoint = projection.toPixels(GeoPoint(boundingBox.latSouth, boundingBox.lonEast), null)

        // --- KORREKTUR: Verwende die Projection, um das sichtbare Rechteck zu erhalten ---
        // --- VERÄNDERT: Entferne die Sichtbarkeitsprüfung ---
        // val screenRect = mapView.projection.screenRect
        // Prüfe, ob das Overlay überhaupt sichtbar ist
        // if (!screenRect.intersects(nwPoint.x, sePoint.y, sePoint.x, nwPoint.y)) {
        // return // Overlay ist nicht im sichtbaren Bereich
        // }
        // --- ENDE VERÄNDERUNG ---

        // Berechne die Position und Größe des Bitmaps auf der Canvas
        val dstLeft = nwPoint.x
        val dstTop = nwPoint.y
        val dstRight = sePoint.x
        val dstBottom = sePoint.y

        // Zeichne das Bitmap in das berechnete Rechteck
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(dstLeft, dstTop, dstRight, dstBottom), paint)
    }
}