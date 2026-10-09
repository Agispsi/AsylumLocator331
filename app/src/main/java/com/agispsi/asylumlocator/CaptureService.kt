package com.agispsi.asylumlocator

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs

class CaptureService : Service() {
    companion object { @Volatile var instance: CaptureService? = null }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private lateinit var imageThread: HandlerThread
    private val lock = Any()
    private var latest: Bitmap? = null
    private var frameTime = 0L
    @Volatile private var captureRequested = false
    @Volatile private var shuttingDown = false
    private var overlay: LinearLayout? = null
    private var calibrationView: View? = null
    private lateinit var statusView: TextView
    private lateinit var calibration: Calibration
    private lateinit var ocr: Ocr
    private lateinit var store: ObservationStore
    private val manager get() = getSystemService(WINDOW_SERVICE) as WindowManager
    private var spec = SearchSpec("goneaway",22)
    private var windowsInspected = 0
    private var found = 0
    private var runLog = StringBuilder()
    private var sessionId = ""
    private val foundKeys = mutableSetOf<String>()
    private val visited = mutableListOf<Bitmap>()
    private var initialRotation = 0
    private var calibrationBitmap: Bitmap? = null

    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); instance = this
        calibration = Calibration(this); ocr = Ocr(); store = ObservationStore(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { cancel("Stopped by user"); stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        try {
            spec = SearchSpec(intent?.getStringExtra("query") ?: "", intent?.getIntExtra("level",0)?.takeIf { it > 0 },331,intent?.getIntExtra("rows",5) ?: 5,intent?.getIntExtra("columns",5) ?: 5)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(NotificationChannel("scan", "Active map scan", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this,1,Intent(this,javaClass).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this,"scan").setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("Asylum Locator — screen capture active").setContentText("Only the game is processed. Tap Stop to end capture.")
                .setContentIntent(open).addAction(Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(331,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(331,notification)
            @Suppress("DEPRECATION") val consent = intent?.getParcelableExtra<Intent>("consent") ?: error("Screen capture consent missing")
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,consent)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { cancel("Screen sharing ended"); stopSelf() }
            },Handler(Looper.getMainLooper()))
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(metrics)
            @Suppress("DEPRECATION") initialRotation = manager.defaultDisplay.rotation
            imageThread = HandlerThread("GameCapture").apply { start() }
            reader = ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,2)
            reader!!.setOnImageAvailableListener({ source ->
                val img = try { source.acquireLatestImage() } catch(_: IllegalStateException) { null } ?: return@setOnImageAvailableListener
                try {
                    if(!captureRequested || shuttingDown) return@setOnImageAvailableListener
                    val now = SystemClock.elapsedRealtime()
                    if (now - frameTime < 120) return@setOnImageAvailableListener
                    val p = img.planes[0]
                    val padding = p.rowStride - p.pixelStride * img.width
                    val padded = Bitmap.createBitmap(img.width + padding / p.pixelStride,img.height,Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(p.buffer)
                    val frame = Bitmap.createBitmap(padded,0,0,img.width,img.height)
                    if (frame !== padded) padded.recycle()
                    synchronized(lock) {
                        if(shuttingDown) frame.recycle()
                        else { latest?.recycle(); latest = frame; frameTime = now }
                    }
                } finally { img.close() }
            },Handler(imageThread.looper))
            display = projection!!.createVirtualDisplay("AsylumCapture",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
            showControls()
            report("Open Last Asylum. Setup calibrates 3 screens. Then open a Sanctuary and tap Read to verify controls for this session.")
            packageManager.getLaunchIntentForPackage(GameAccessService.GAME)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { startActivity(it) }
        } catch (e: Exception) { getSharedPreferences("status",MODE_PRIVATE).edit().putString("last", "Capture failed: ${e.message}").apply(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun params(full: Boolean = false) = WindowManager.LayoutParams(
        if(full) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        if(full) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = if(full) 0 else 50 }
    private fun showControls() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10,8,10,8); setBackgroundColor(Color.rgb(16,32,48)) }
        statusView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; maxWidth = (resources.displayMetrics.density*300).toInt(); maxLines = 5 }
        panel.addView(statusView)
        val row = LinearLayout(this)
        fun button(label: String, block: () -> Unit) { row.addView(Button(this).apply { text = label; textSize=11f; minWidth=0; minimumWidth=0; setPadding(12,0,12,0); setOnClickListener { block() } }) }
        button("Scan") { startScan() }
        button("Stop") { cancel("Cancelled by user") }
        button("Read") { launchWork { requireReady(false); inspect(null,true); report("Panel/coordinate round trip verified this session. Close the panel and tap Scan. ${calibration.summary()}") } }
        button("Setup") { showSetup() }
        button("Exit") { cancel("Capture ended"); stopSelf() }
        panel.addView(row)
        overlay = panel; manager.addView(panel,params())
        // Drag the status strip away from game controls. Buttons remain tappable.
        var sx=0f; var sy=0f; var ox=0; var oy=0
        statusView.setOnTouchListener { _,e ->
            val p = panel.layoutParams as WindowManager.LayoutParams
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=p.x; oy=p.y }
                MotionEvent.ACTION_MOVE -> { p.x=ox+(e.rawX-sx).toInt(); p.y=oy+(e.rawY-sy).toInt(); manager.updateViewLayout(panel,p) }
            }; true
        }
    }
    private fun showSetup() {
        if (work?.isActive == true) { report("Stop the current scan before calibration."); return }
        val panel = overlay ?: return
        if (panel.childCount > 2) { panel.removeViewAt(2); return }
        val row = LinearLayout(this)
        listOf("Map" to "map", "Sanctuary" to "detail", "Add Tag" to "tag").forEach { (label,group) ->
            row.addView(Button(this).apply { text=label; textSize=11f; setOnClickListener { panel.removeView(row); calibrate(group) } })
        }
        panel.addView(row); report("Navigate to the screen first; then choose its calibration button. Drag this status strip to move controls.")
    }
    private fun calibrate(group: String) = launchWork {
        val frame = capture()
        calibrationBitmap = frame
        overlay?.visibility = View.GONE
        val view = CalibrationView(this,frame,group) { values ->
            try { if(values != null) calibration.save(values,frame) }
            catch(e: Exception) { report("Calibration failed: ${e.message}") }
            calibrationView?.let { manager.removeView(it) }; calibrationView=null
            frame.recycle(); calibrationBitmap=null; overlay?.visibility=View.VISIBLE
            report("${calibration.summary()}. After all 3, open a Sanctuary and tap Read.")
        }
        calibrationView=view; manager.addView(view,params(true))
    }
    private fun launchWork(block: suspend () -> Unit) {
        if(work?.isActive == true || calibrationView != null) return
        work = scope.launch {
            try { withTimeout(4 * 60 * 60 * 1000L) { block() } }
            catch(e: TimeoutCancellationException) { report(coverage("Stopped: screen response timed out")) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { report(coverage("Stopped: ${e.message}")) }
            finally { overlay?.visibility=View.VISIBLE; saveLog() }
        }
    }
    fun cancel(reason: String) {
        work?.cancel(); work=null
        if (::statusView.isInitialized) report(coverage(reason))
        saveLog()
    }
    private fun requireReady(automatic: Boolean) {
        check(calibration.ready()) { "Complete Map, Sanctuary, and Add Tag calibration first." }
        check(GameAccessService.instance != null) { "Enable Asylum map controls in Accessibility settings." }
        if(automatic) check(calibration.verifiedThisSession) { "Open a known Sanctuary and tap Read to verify the panel/coordinate round trip first." }
    }
    private suspend fun capture(): Bitmap {
        currentCoroutineContext().ensureActive()
        check(GameAccessService.instance?.gameForeground() == true) { "Game is not foreground. Capture paused." }
        @Suppress("DEPRECATION") check(manager.defaultDisplay.rotation == initialRotation) { "Screen rotated. Exit capture, restart and recalibrate." }
        // Hiding the overlay causes a fresh composition even if the underlying map is static.
        val since=SystemClock.elapsedRealtime()
        captureRequested=true
        overlay?.visibility=View.GONE
        try {
            delay(260)
            return withTimeout(4500) {
                var image: Bitmap? = null
                while(image == null) {
                    synchronized(lock) { if(frameTime >= since && latest != null) image=latest!!.copy(Bitmap.Config.ARGB_8888,false) }
                    if(image == null) delay(80)
                }
                if(GameAccessService.instance?.gameForeground() != true) { image!!.recycle(); error("Game lost focus during capture.") }
                image!!
            }
        } finally { captureRequested=false; overlay?.visibility=View.VISIBLE }
    }
    private suspend fun guardedFrame(key: String): Bitmap {
        var frame: Bitmap? = null
        repeat(5) {
            frame?.recycle(); frame=capture()
            if(calibration.guard(key,frame!!)) return frame!!
            delay(450)
        }
        frame?.recycle(); error("Expected ${key.removeSuffix("Guard")} screen was not recognized. No further taps sent; check calibration or close unexpected panels.")
    }
    private suspend fun tap(point: PointF) {
        currentCoroutineContext().ensureActive()
        overlay?.visibility=View.GONE
        try {
            delay(150)
            check(GameAccessService.instance?.gesture(point) == true) { "Tap cancelled or unavailable." }
            delay(650)
        } finally { overlay?.visibility=View.VISIBLE }
    }
    private suspend fun identity(frame: Bitmap): PlayerIdentity = LocatorLogic.identity(ocr.text(frame,calibration.rect("name")),ocr.text(frame,calibration.rect("level")))
        ?: error("Player name or Sanctuary level is ambiguous. Recalibrate tight crops; exclude VIP and other numbers.")
    private suspend fun inspect(expectedLabel: String?, manual: Boolean) {
        val detail = guardedFrame("detailGuard")
        var tagEvidence: Bitmap? = null
        try {
            val p=identity(detail)
            val second=guardedFrame("detailGuard")
            val p2=try { identity(second) } finally { second.recycle() }
            check(LocatorLogic.same(p,p2)) { "Player text changed between frames." }
            if(expectedLabel != null) check(LocatorLogic.key(p.name) == LocatorLogic.key(expectedLabel)) { "Tapped player differs from the detected map label. Check name-to-building calibration." }
            if(!manual && !spec.matches(p)) { tap(calibration.point("detailClose")); guardedFrame("mapGuard").recycle(); return }
            // Never mix a screen center coordinate with a Sanctuary position: read Add Tag only.
            tap(calibration.point("star"))
            val tag=guardedFrame("tagGuard"); tagEvidence=tag
            val c=LocatorLogic.coordinates(ocr.text(tag,calibration.rect("coords"))) ?: error("Add Tag coordinates are unreadable or ambiguous; nothing saved.")
            val tag2=guardedFrame("tagGuard")
            val c2=try { LocatorLogic.coordinates(ocr.text(tag2,calibration.rect("coords"))) } finally { tag2.recycle() }
            check(c2 == c && c.server == spec.server) { "Coordinates changed or server is not #${spec.server}; nothing saved." }
            tap(calibration.point("tagClose"))
            val restored=guardedFrame("detailGuard")
            val restoredPlayer=try { identity(restored) } finally { restored.recycle() }
            val verificationSpec=if(manual) spec.copy(query=p.name,level=p.level) else spec
            check(LocatorLogic.verifiedChain(p,p2,restoredPlayer,c,c2!!,verificationSpec)) { "Could not associate coordinates with the same Sanctuary after closing Add Tag." }
            calibration.verifiedThisSession=true
            if(spec.matches(p)) {
                withContext(Dispatchers.IO) { store.save(p,c,detail,tag) }
                foundKeys += LocatorLogic.observationKey(p,c)
                found=foundKeys.size
                report("Observed ${p.display}, level ${p.level}, $c. Screenshot evidence saved; review OCR in Results.")
            } else report("Verified ${p.display}, level ${p.level}, $c; does not match current search. Controls verified.")
            if(!manual) { tap(calibration.point("detailClose")); guardedFrame("mapGuard").recycle() }
        } finally { detail.recycle(); tagEvidence?.recycle() }
    }
    private fun startScan() = launchWork {
        requireReady(true)
        windowsInspected=0; found=0
        sessionId=System.currentTimeMillis().toString(); runLog=StringBuilder()
        visited.forEach { it.recycle() }; visited.clear()
        foundKeys.clear()
        report("Starting ${spec.rows} × ${spec.columns} overlapping views from the current map position. Keep the game open and do not touch the map.")
        for(index in 0 until spec.rows*spec.columns) {
            currentCoroutineContext().ensureActive()
            val map=guardedFrame("mapGuard")
            val region=calibration.rect("map")
            val crop=Bitmap.createBitmap(map,region.left,region.top,region.width(),region.height())
            val fingerprint=Bitmap.createScaledBitmap(crop,80,80,true)
            if(crop !== fingerprint) crop.recycle()
            if(visited.any { Calibration.imageDistance(it,fingerprint) < 0.025 }) {
                fingerprint.recycle(); map.recycle(); error("Repeated map view or map boundary detected. Sweep stopped; unvisited areas remain.")
            }
            visited += fingerprint
            val candidates=try { ocr.lines(map,region).filter { LocatorLogic.matches(it.text,spec.query) } } finally { map.recycle() }
            check(candidates.size <= 80) { "Too many ambiguous candidates in one view. Use a longer name." }
            for(candidate in candidates) {
                currentCoroutineContext().ensureActive()
                val fresh=guardedFrame("mapGuard")
                val current=try { ocr.lines(fresh,region).filter { LocatorLogic.key(it.text) == LocatorLogic.key(candidate.text) }
                    .minByOrNull { abs(it.box.centerX()-candidate.box.centerX())+abs(it.box.centerY()-candidate.box.centerY()) } } finally { fresh.recycle() }
                check(current != null && abs(current.box.centerX()-candidate.box.centerX()) < 45 && abs(current.box.centerY()-candidate.box.centerY()) < 45) { "Map moved during inspection. Restart from the current view." }
                val offset=calibration.point("base"); val label=calibration.point("label")
                val point=PointF(current!!.box.exactCenterX()+offset.x-label.x,current.box.exactCenterY()+offset.y-label.y)
                check(region.contains(point.x.toInt(),point.y.toInt())) { "Candidate is too close to viewport edge. Reposition the map and restart." }
                report("View ${index+1}: inspecting ${candidate.text}")
                tap(point); inspect(candidate.text,false)
            }
            windowsInspected++
            report(coverage("View ${index+1} processed (${candidates.size} readable candidate labels)"))
            val direction=LocatorLogic.nextMove(index,spec.rows,spec.columns) ?: break
            guardedFrame("mapGuard").recycle()
            val cx=region.exactCenterX(); val cy=region.exactCenterY(); val dx=region.width()*0.25f; val dy=region.height()*0.25f
            val from=when(direction) { "left" -> PointF(cx+dx,cy); "right" -> PointF(cx-dx,cy); else -> PointF(cx,cy+dy) }
            val to=when(direction) { "left" -> PointF(cx-dx,cy); "right" -> PointF(cx+dx,cy); else -> PointF(cx,cy-dy) }
            overlay?.visibility=View.GONE
            try { delay(150); check(GameAccessService.instance?.gesture(from,to,1100) == true) { "Map swipe cancelled." }; delay(1500) }
            finally { overlay?.visibility=View.VISIBLE }
        }
        report(coverage("Planned sweep finished"))
    }
    private fun coverage(reason: String) = LocatorLogic.coverage(windowsInspected,spec.rows*spec.columns,found,reason)
    private fun report(message: String) {
        if(::statusView.isInitialized) statusView.text=message
        getSharedPreferences("status",MODE_PRIVATE).edit().putString("last",message).apply()
        runLog.append(System.currentTimeMillis()).append(' ').append(message).append('\n')
        saveLog()
    }
    private fun saveLog() {
        if(sessionId.isNotEmpty()) runCatching { File(filesDir,"last-scan.txt").writeText("Session $sessionId; query=${spec.query}; level=${spec.level}; server=${spec.server}\n$runLog") }
    }
    override fun onDestroy() {
        shuttingDown=true; captureRequested=false; instance=null; scope.cancel()
        overlay?.let { runCatching { manager.removeView(it) } }; calibrationView?.let { runCatching { manager.removeView(it) } }
        calibrationBitmap?.recycle(); calibrationBitmap=null
        display?.release(); reader?.setOnImageAvailableListener(null,null); reader?.close(); projection?.stop(); projection=null
        if(::imageThread.isInitialized) { imageThread.quitSafely() }
        synchronized(lock) { latest?.recycle(); latest=null }
        visited.forEach { it.recycle() }
        if(::ocr.isInitialized) ocr.close()
        if(::store.isInitialized) store.close()
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
