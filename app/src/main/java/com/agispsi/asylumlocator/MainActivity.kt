package com.agispsi.asylumlocator

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private fun playerNameWithoutAlliance(raw: String): String =
        raw.trim().replace(Regex("^(?:\\s*\\[[^]\\r\\n]*]\\s*)+"), "").trim()

    private fun matchesPlayerName(rawPlayer: String, query: String): Boolean {
        val needle = playerNameWithoutAlliance(query).lowercase(Locale.ROOT)
        return needle.isNotEmpty() &&
            playerNameWithoutAlliance(rawPlayer).lowercase(Locale.ROOT).contains(needle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 24)
        }
        layout.addView(TextView(this).apply {
            text = "Asylum Locator — Server 331"
            textSize = 22f
        })
        val username = EditText(this).apply {
            hint = "Name or part of name (e.g. goneaway)"
            setSingleLine(true)
        }
        layout.addView(username)
        val level = EditText(this).apply {
            hint = "Sanctuary level (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(level)
        val output = TextView(this).apply {
            text = "Search matches any capitalization and any position in the player name, ignoring [alliance] tags. Live map scanning is not yet available."
            textSize = 16f
            setPadding(0, 28, 0, 0)
        }
        layout.addView(Button(this).apply {
            text = "Search"
            setOnClickListener {
                val q = playerNameWithoutAlliance(username.text.toString())
                val l = level.text.toString().trim()
                output.text = if (q.isEmpty()) {
                    "Enter all or part of a player name."
                } else {
                    val examples = listOf("[GNA] GoneAway", "[ABC] xGONEAWAYx", "[OUT] goneaway123", "[GNA] SomeoneElse")
                    val matches = examples.filter { matchesPlayerName(it, q) }
                    "Search rule: name contains \"$q\" (case-insensitive); ignore alliance tags." +
                        (if (l.isNotEmpty()) " Sanctuary level: $l." else "") +
                        "\n\nExample matches (NOT live players):\n" +
                        (if (matches.isEmpty()) "None in example list" else matches.joinToString("\n")) +
                        "\n\nLive coordinates are unavailable until a real map-reading mechanism is implemented."
                }
            }
        })
        layout.addView(output)
        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }
}
