package com.agispsi.asylumlocator

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

class Calibration(private val context: Context) {
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)
    private var data = JSONObject(prefs.getString("profile", "{}")!!)
    var verifiedThisSession = false
    val width get() = data.optInt("width")
    val height get() = data.optInt("height")
    fun rect(key: String): Rect {
        val a = data.getJSONArray(key)
        return Rect(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
    }
    fun point(key: String): PointF {
        val a = data.getJSONArray(key)
        return PointF(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
    }
    fun ready() = listOf("map", "mapGuard", "label", "base", "name", "level", "detailGuard", "star", "detailClose", "coords", "tagGuard", "tagClose").all { data.has(it) }
    fun summary() = listOf("Map" to "map", "Sanctuary" to "name", "Add Tag" to "coords").joinToString(" • ") { "${it.first}: ${if (data.has(it.second)) "set" else "needed"}" }
    fun save(values: Map<String, FloatArray>, screenshot: Bitmap) {
        if (width != screenshot.width || height != screenshot.height) data = JSONObject()
        data.put("width", screenshot.width).put("height", screenshot.height)
        values.forEach { (key, v) -> data.put(key, JSONArray(v.map { it.toInt() })) }
        values.keys.filter { it.endsWith("Guard") }.forEach { key ->
            val r = rect(key)
            val crop = Bitmap.createBitmap(screenshot, r.left, r.top, r.width(), r.height())
            File(context.filesDir, "$key.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if(crop !== screenshot) crop.recycle()
        }
        prefs.edit().putString("profile", data.toString()).apply()
        verifiedThisSession = false
    }
    fun guard(key: String, frame: Bitmap): Boolean {
        if (frame.width != width || frame.height != height || !data.has(key)) return false
        val reference = BitmapFactory.decodeFile(File(context.filesDir, "$key.png").path) ?: return false
        val r = rect(key)
        val crop = Bitmap.createBitmap(frame, r.left, r.top, r.width(), r.height())
        val distance = imageDistance(reference, crop)
        reference.recycle(); if(crop !== frame) crop.recycle()
        return distance < 0.085
    }
    companion object {
        fun imageDistance(a: Bitmap, b: Bitmap): Double {
            val x = Bitmap.createScaledBitmap(a, 40, 40, true)
            val y = Bitmap.createScaledBitmap(b, 40, 40, true)
            var distance = 0.0
            for (j in 0 until 40) for (i in 0 until 40) {
                val p = x.getPixel(i, j); val q = y.getPixel(i, j)
                distance += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q))
            }
            if (x !== a) x.recycle()
            if (y !== b) y.recycle()
            return distance / (40.0 * 40 * 3 * 255)
        }
    }
}

data class CalibrationStep(val key: String, val text: String, val box: Boolean)
class CalibrationView(context: Context, private val screenshot: Bitmap, group: String,
    private val done: (Map<String, FloatArray>?) -> Unit) : View(context) {
    private val steps = when (group) {
        "map" -> listOf(
            CalibrationStep("map", "Drag around usable map terrain. Exclude every HUD button and chat area.", true),
            CalibrationStep("mapGuard", "Drag around a fixed icon visible ONLY on the map with no panels. Avoid animated areas.", true),
            CalibrationStep("label", "Tap the center of one visible player's printed name (not its alliance-only line).", false),
            CalibrationStep("base", "Tap the center of THAT player's Sanctuary building. This teaches the name-to-building offset.", false))
        "detail" -> listOf(
            CalibrationStep("name", "Drag tightly around ONE line containing the actual player name, including alliance if present.", true),
            CalibrationStep("level", "Drag around Sanctuary level only: e.g. 22 or Lv.22. Exclude VIP and power.", true),
            CalibrationStep("detailGuard", "Drag around a fixed title/icon unique to this Sanctuary info panel. Exclude player-specific text.", true),
            CalibrationStep("star", "Tap the star which opens Add Tag for this Sanctuary.", false),
            CalibrationStep("detailClose", "Tap the control which closes this panel and returns to the map.", false))
        else -> listOf(
            CalibrationStep("coords", "Drag around the COMPLETE coordinate line including #server, X and Y.", true),
            CalibrationStep("tagGuard", "Drag around the fixed Add Tag heading. Exclude editable text.", true),
            CalibrationStep("tagClose", "Tap Cancel/close that returns to the SAME Sanctuary panel. Do not select Save.", false))
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val target = RectF()
    private val values = linkedMapOf<String, FloatArray>()
    private var step = 0
    private var start: PointF? = null
    private var end: PointF? = null
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = v * density
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(15, 23, 42))
        val scale = min(width.toFloat() / screenshot.width, (height - dp(220)) / screenshot.height)
        val w = screenshot.width * scale; val h = screenshot.height * scale
        target.set((width - w) / 2, dp(155).toFloat(), (width + w) / 2, dp(155) + h)
        canvas.drawBitmap(screenshot, null, target, null)
        paint.color = Color.WHITE; paint.textSize = dp(17)
        canvas.drawText("Calibration ${step + 1}/${steps.size}", dp(12), dp(25), paint)
        paint.textSize = dp(14)
        var line = ""; var y = dp(52)
        steps[step].text.split(" ").forEach { word ->
            if (paint.measureText("$line $word") > width - dp(24)) { canvas.drawText(line, dp(12), y, paint); y += dp(21); line = word }
            else line = if (line.isEmpty()) word else "$line $word"
        }
        canvas.drawText(line, dp(12), y, paint)
        paint.color = Color.CYAN; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2)
        start?.let { s -> end?.let { e -> if (steps[step].box) canvas.drawRect(min(s.x,e.x), min(s.y,e.y),max(s.x,e.x),max(s.y,e.y),paint) else canvas.drawCircle(e.x,e.y,dp(8),paint) } }
        paint.style = Paint.Style.FILL; paint.textSize = dp(16)
        canvas.drawText("CANCEL", dp(16), height - dp(23), paint)
        canvas.drawText(if (step == steps.lastIndex) "SAVE" else "NEXT", width - dp(85), height - dp(23), paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && event.y > height - dp(65)) {
            if (event.x < width / 2) { done(null); return true }
            val s = start ?: return true; val e = end ?: return true
            fun ix(x: Float) = ((x - target.left) / target.width() * screenshot.width).coerceIn(0f, screenshot.width.toFloat() - 1)
            fun iy(y: Float) = ((y - target.top) / target.height() * screenshot.height).coerceIn(0f, screenshot.height.toFloat() - 1)
            val v = if (steps[step].box) floatArrayOf(ix(min(s.x,e.x)), iy(min(s.y,e.y)), ix(max(s.x,e.x)), iy(max(s.y,e.y))) else floatArrayOf(ix(e.x), iy(e.y))
            if (steps[step].box && (v[2] - v[0] < 12 || v[3] - v[1] < 12)) return true
            values[steps[step].key] = v
            if (++step == steps.size) done(values) else { start = null; end = null; invalidate() }
            return true
        }
        if (!target.contains(event.x, event.y)) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { start = PointF(event.x,event.y); end = start }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> end = PointF(event.x,event.y)
        }
        invalidate(); return true
    }
}
