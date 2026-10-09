package com.agispsi.asylumlocator

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,48,36,24) }
  fun heading(s:String) = TextView(this).apply { text=s; textSize=22f; setPadding(0,0,0,20) }
  layout.addView(heading("Asylum Locator — Server 331"))
  val username=EditText(this).apply { hint="Player username (case insensitive)"; setSingleLine(true) };layout.addView(username)
  val level=EditText(this).apply { hint="Sanctuary level"; inputType=InputType.TYPE_CLASS_NUMBER };layout.addView(level)
  val output=TextView(this).apply { text="No live map connection is available. This prototype cannot automatically find players or retrieve their coordinates."; textSize=16f; setPadding(0,28,0,0) }
  layout.addView(Button(this).apply { text="Search"; setOnClickListener { val n=username.text.toString().trim(); val l=level.text.toString().trim(); output.text=if(n.isEmpty()||l.isEmpty()) "Enter a username and Sanctuary level." else "No verified player-location data source. Cannot locate ${n} (Sanctuary ${l}) on server 331." } })
  layout.addView(output)
  val scroll=ScrollView(this);scroll.addView(layout);setContentView(scroll)
 }
}
