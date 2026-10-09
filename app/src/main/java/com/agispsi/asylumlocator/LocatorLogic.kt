package com.agispsi.asylumlocator

import java.text.Normalizer
import java.util.Locale

data class PlayerIdentity(val display: String, val name: String, val alliance: String?, val level: Int)
data class Coordinates(val server: Int, val x: Int, val y: Int) {
    override fun toString() = "#$server X:$x Y:$y"
}
data class SearchSpec(val query: String, val level: Int?, val server: Int = 331, val rows: Int = 5, val columns: Int = 5) {
    init {
        require(LocatorLogic.cleanName(query).isNotBlank()) { "Enter a player name, not just an alliance tag." }
        require(level == null || level in 1..999) { "Level must be 1–999." }
        require(server > 0 && rows in 1..20 && columns in 1..20)
    }
    fun matches(p: PlayerIdentity) = LocatorLogic.matches(p.display, query) && (level == null || p.level == level)
}
object LocatorLogic {
    private val tags = Regex("\\[[^\\[\\]\\r\\n]*]")
    fun cleanName(raw: String): String = tags.replace(Normalizer.normalize(raw, Normalizer.Form.NFKC), "").trim().replace(Regex("\\s+"), " ")
    fun key(raw: String) = cleanName(raw).lowercase(Locale.ROOT)
    fun matches(raw: String, query: String): Boolean {
        val needle = key(query)
        return needle.isNotEmpty() && key(raw).contains(needle)
    }
    fun identity(nameText: String, levelText: String): PlayerIdentity? {
        val display = nameText.trim()
        // A multiline crop or unmatched bracket is ambiguous: request recalibration.
        if (display.isBlank() || display.contains('\n') || display.count { it == '[' } != display.count { it == ']' }) return null
        val name = cleanName(display)
        if (name.isEmpty() || name.length > 80) return null
        val level = Regex("(?i)^(?:sanctuary\\s*)?(?:(?:level|lv)\\.?\\s*[:：]?\\s*)?(\\d{1,3})$")
            .matchEntire(levelText.trim().replace(Regex("\\s+"), " "))?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (level !in 1..999) return null
        return PlayerIdentity(display, name, tags.find(display)?.value?.removeSurrounding("[", "]"), level)
    }
    fun coordinates(raw: String): Coordinates? {
        val re = Regex("(?i)(?<![\\d#])#\\s*(\\d{1,6})\\s+X\\s*[:：]\\s*(\\d{1,6})\\s+Y\\s*[:：]\\s*(\\d{1,6})(?!\\d)")
        val all = re.findAll(raw).toList()
        if (all.size != 1) return null
        val g = all.single().groupValues
        return Coordinates(g[1].toInt(), g[2].toInt(), g[3].toInt()).takeIf { it.server > 0 }
    }
    fun same(a: PlayerIdentity, b: PlayerIdentity) = key(a.display) == key(b.display) && a.level == b.level && a.alliance == b.alliance
    fun observationKey(p: PlayerIdentity, c: Coordinates) = "${c.server}:${c.x}:${c.y}:${key(p.name)}"
    fun verifiedChain(first: PlayerIdentity, second: PlayerIdentity, restored: PlayerIdentity, a: Coordinates, b: Coordinates, spec: SearchSpec): Boolean =
        same(first, second) && same(first, restored) && a == b && a.server == spec.server && spec.matches(first)
    // Screen-space serpentine route. This intentionally makes no claim about world-coordinate coverage.
    fun nextMove(index: Int, rows: Int, columns: Int): String? {
        require(rows in 1..20 && columns in 1..20 && index in 0 until rows * columns)
        if (index == rows * columns - 1) return null
        if (index % columns == columns - 1) return "up"
        return if ((index / columns) % 2 == 0) "left" else "right"
    }
    fun coverage(windows: Int, planned: Int, matches: Int, reason: String) =
        "$windows/$planned planned views inspected; $matches matching observations. $reason. Search incomplete: only readable labels in visited views were checked; server-wide coverage is unknown."
}
