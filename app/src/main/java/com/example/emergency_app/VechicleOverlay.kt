import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import com.example.emergency_app.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class VehicleOverlay(
    private val mapView: MapView,
    context: Context
) : Overlay() {

    private val vehicleBitmap =
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_ambulance)

    private val vehicleBitmapFlipped =
        BitmapFactory.decodeResource(context.resources, R.drawable.ic_ambulance_flipped)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var position: GeoPoint? = null
    var bearing: Float = 0f   // degrees, 0 = north

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pos = position ?: return

        val point = Point()
        mapView.projection.toPixels(pos, point)

        val shouldFlip = bearing in 180f..320f

        val bitmap = if (shouldFlip) vehicleBitmapFlipped else vehicleBitmap

        canvas.save()

        // Move origin to vehicle position
        canvas.translate(point.x.toFloat(), point.y.toFloat())

        // Correct rotation:
        // If flipped, subtract 180 so it doesn't go upside down
        val rotation = bearing //if (shouldFlip) bearing - 180f else bearing
        canvas.rotate(rotation)

        // Draw centered
        canvas.drawBitmap(
            bitmap,
            -bitmap.width / 2f,
            -bitmap.height / 2f,
            paint
        )

        canvas.restore()
    }
}
