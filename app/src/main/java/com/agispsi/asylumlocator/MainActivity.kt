package com.agispsi.asylumlocator

import android.Manifest
import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var name: EditText
    private lateinit var level: EditText
    private lateinit var rows: EditText
    private lateinit var columns: EditText
    private lateinit var store: ObservationStore
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private var pending: SearchSpec? = null
    private var exportBody = ""
    private val prefs get() = getSharedPreferences("search",MODE_PRIVATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store=ObservationStore(this)
        window.statusBarColor=Color.rgb(15,32,48)
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,36,28,32); setBackgroundColor(Color.rgb(245,248,250)) }
        setContentView(ScrollView(this).apply { addView(body) })
        text("Asylum Locator 331",25f)
        text("Live screen scanner • moderator utility",15f)
        text("Searches readable map labels, opens matching Sanctuaries, reads Add Tag coordinates, and saves evidence. No game database or full-server guarantee. Game compatibility has not been independently verified.")
        name=field("Player name or partial name",prefs.getString("query","goneaway")!!)
        level=field("Sanctuary level (blank = any)",prefs.getString("level","22")!!,true)
        rows=field("Rows of map views (1–20)",prefs.getString("rows","5")!!,true)
        columns=field("Views per row (1–20)",prefs.getString("columns","5")!!,true)
        text("Coverage starts at your current map position, sweeps across overlapping views, then moves down a row. Keep the same zoom and orientation used for calibration. Hidden/unreadable labels remain unsearched.")
        button("1. Allow floating controls") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        button("2. Enable Asylum map controls") {
            AlertDialog.Builder(this).setTitle("Accessibility use").setMessage("This service checks whether Last Asylum is foreground and sends only calibrated map taps/swipes during a scan you start. Screenshots and extracted names/coordinates stay on this phone unless you export them. You can stop with the overlay or notification. On some sideloaded Android apps, App info → ⋮ → Allow restricted settings is needed before enabling accessibility.")
                .setPositiveButton("Open settings") { _,_ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.setNegativeButton("Cancel",null).show()
        }
        button("3. Start screen capture / open game") { begin() }
        button("Stop capture") { CaptureService.instance?.cancel("Stopped from app"); stopService(Intent(this,CaptureService::class.java)); refresh() }
        text("First use on your phone:\n• In game, use Setup → Map with names visible. Select the map area, a fixed map-only icon, one name center and that Sanctuary's center.\n• Open a Sanctuary: Setup → Sanctuary. Mark its name, level, fixed panel icon, star and close control.\n• Open Add Tag: Setup → Add Tag. Mark the entire #331 X:… Y:… line, heading and Cancel/close control.\n• Return to that Sanctuary and tap Read. It checks the name/level twice, reads coordinates twice, then verifies it returned to the same player.\n• Close the panel and tap Scan. Drag the status strip to move the controls. Stop cancels scanning; Exit ends capture.")
        status=text("",14f)
        button("Refresh results") { refresh() }
        button("Export observations and scan log") {
            exportBody=JSONObject(store.export()).put("lastScanLog",File(filesDir,"last-scan.txt").takeIf { it.exists() }?.readText() ?: "No automatic scan performed").toString(2)
            @Suppress("DEPRECATION") startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"asylum-observations.json"),44)
        }
        button("Delete observations and screenshots") {
            AlertDialog.Builder(this).setMessage("Permanently delete all local observations and their evidence?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> store.clear(); refresh() }.show()
        }
        text("Recorded observations",21f)
        text("These are locations at the recorded time. A player can move. OCR can make repeatable errors: tap each result to compare its screenshots before treating it as confirmed.")
        results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; body.addView(results)
    }
    override fun onResume() { super.onResume(); if(::results.isInitialized) refresh() }
    private fun text(value: String,size: Float=14f): TextView = TextView(this).apply { text=value; textSize=size; setTextColor(Color.rgb(20,35,50)); setPadding(0,12,0,12); body.addView(this) }
    private fun field(hintText: String,value: String,numeric: Boolean=false): EditText {
        text(hintText,13f)
        return EditText(this).apply { hint=hintText; setText(value); setSingleLine(); if(numeric) inputType=InputType.TYPE_CLASS_NUMBER; body.addView(this) }
    }
    private fun button(label: String,action: () -> Unit) { body.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
    private fun begin() {
        try {
            check(CaptureService.instance == null) { "Capture is already active. Stop it before changing the search." }
            check(Settings.canDrawOverlays(this)) { "Allow floating controls first." }
            check(GameAccessService.instance != null) { "Enable Asylum map controls first." }
            val levelText=level.text.toString().trim()
            pending=SearchSpec(name.text.toString(),if(levelText.isBlank()) null else levelText.toInt(),331,rows.text.toString().toInt(),columns.text.toString().toInt())
            prefs.edit().putString("query",name.text.toString()).putString("level",levelText).putString("rows",rows.text.toString()).putString("columns",columns.text.toString()).apply()
            if(Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),45)
            } else requestCapture()
        } catch(e: Exception) { AlertDialog.Builder(this).setMessage(e.message ?: "Invalid search settings").setPositiveButton("OK",null).show() }
    }
    private fun requestCapture() {
        val manager=getSystemService(MediaProjectionManager::class.java)
        val captureIntent=if(Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
        @Suppress("DEPRECATION") startActivityForResult(captureIntent,43)
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode == 45 && pending != null) requestCapture()
    }
    @Deprecated("Android activity result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode == 43 && resultCode == RESULT_OK && data != null) {
            val spec=pending ?: return
            startForegroundService(Intent(this,CaptureService::class.java).putExtra("consent",data).putExtra("query",spec.query).putExtra("level",spec.level ?: 0).putExtra("rows",spec.rows).putExtra("columns",spec.columns))
        }
        if(requestCode == 44 && resultCode == RESULT_OK) data?.data?.let { uri ->
            try { (contentResolver.openOutputStream(uri) ?: error("Cannot open export destination")).bufferedWriter().use { it.write(exportBody) }; Toast.makeText(this,"Export saved",Toast.LENGTH_SHORT).show() }
            catch(e: Exception) { Toast.makeText(this,"Export failed: ${e.message}",Toast.LENGTH_LONG).show() }
        }
    }
    private fun refresh() {
        status.text="${Calibration(this).summary()}\n${getSharedPreferences("status",MODE_PRIVATE).getString("last","No scan has been performed. No map data has been read.")}"
        results.removeAllViews()
        val all=store.all()
        val query=name.text.toString()
        val wantedLevel=level.text.toString().trim().toIntOrNull()
        val filtered=all.filter { (query.isBlank() || LocatorLogic.matches(it.getString("name"),query)) && (wantedLevel == null || it.getInt("level") == wantedLevel) }
        results.addView(TextView(this).apply { text="${filtered.size} saved observations matching these filters (${all.size} total). This is not a full-server result." })
        filtered.forEach { result ->
            results.addView(Button(this).apply {
                text="${if(result.getString("alliance").isBlank()) "" else "[${result.getString("alliance")}] "}${result.getString("name")} • level ${result.getInt("level")}\n#${result.getInt("server")} X:${result.getInt("x")} Y:${result.getInt("y")}\n${DateFormat.getDateTimeInstance().format(Date(result.getLong("seen")))}\n${if(result.getInt("verified") == 1) "Visually confirmed on this phone" else "OCR observation — tap to review evidence"}"
                setOnClickListener { evidence(result) }
            })
        }
    }
    private fun evidence(result: JSONObject) {
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply { text="Compare the player name, Sanctuary level and Add Tag coordinates. Confirm only if every value is correct. This records your visual check, not server-wide coverage." })
        val bitmaps=mutableListOf<android.graphics.Bitmap>()
        for(key in listOf("detail","tag")) {
            val bitmap=BitmapFactory.decodeFile(File(File(filesDir,"evidence"),result.getString(key)).path)
            if(bitmap != null) { bitmaps += bitmap; layout.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true }) }
        }
        AlertDialog.Builder(this).setTitle(result.getString("name")).setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Confirm evidence correct") { _,_ -> if(bitmaps.size == 2) { store.confirm(result.getString("id")); refresh() } }
            .setNegativeButton("Close",null).create().apply { setOnDismissListener { bitmaps.forEach { it.recycle() } }; show() }
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}
