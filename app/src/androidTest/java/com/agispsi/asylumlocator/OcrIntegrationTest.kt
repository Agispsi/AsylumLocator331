package com.agispsi.asylumlocator

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

// Rendered fixtures test real bundled ML Kit on Android. They are NOT live-game screenshots.
@RunWith(AndroidJUnit4::class)
class OcrIntegrationTest {
    private fun fixture(text: String): Bitmap = Bitmap.createBitmap(1300,180,Bitmap.Config.ARGB_8888).apply {
        val canvas=Canvas(this); canvas.drawColor(Color.WHITE)
        canvas.drawText(text,30f,110f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=64f; typeface=Typeface.create("sans-serif",Typeface.NORMAL) })
    }
    @Test fun realOcrRecognizesNameAndCoordinates() = runBlocking {
        Ocr().use { ocr ->
            val name=fixture("[ABC] xGONEAWAYx")
            val level=fixture("Sanctuary Level 22")
            val coords=fixture("#331 X:123 Y:456")
            try {
                val p=LocatorLogic.identity(ocr.lines(name).joinToString(" ") { it.text },ocr.lines(level).joinToString(" ") { it.text })
                assertNotNull(p); assertTrue(SearchSpec("goneaway",22).matches(p!!))
                assertEquals(Coordinates(331,123,456),LocatorLogic.coordinates(ocr.lines(coords).joinToString(" ") { it.text }))
            } finally { name.recycle(); level.recycle(); coords.recycle() }
        }
    }
    @Test fun blankImageCreatesNoCoordinates() = runBlocking {
        Ocr().use { ocr -> val blank=fixture(""); try { assertNull(LocatorLogic.coordinates(ocr.lines(blank).joinToString(" ") { it.text })) } finally { blank.recycle() } }
    }
    @Test fun databaseDeduplicatesAndPreservesEvidence() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ObservationStore(context); store.clear()
        val detail=fixture("Test evidence"); val tag=fixture("#331 X:123 Y:456")
        try {
            val p=PlayerIdentity("[A] TestReader","TestReader","A",22)
            val c=Coordinates(331,123,456)
            store.save(p,c,detail,tag); store.save(p,c,detail,tag)
            assertEquals(1,store.all().size)
            val item=store.all().single()
            assertTrue(java.io.File(context.filesDir,"evidence/${item.getString("tag")}").isFile)
            assertEquals(0,item.getInt("verified"))
            store.confirm(item.getString("id")); assertEquals(1,store.all().single().getInt("verified"))
            store.save(p.copy(name="Other",display="Other"),c,detail,tag); assertEquals(2,store.all().size)
        } finally { store.clear(); store.close(); detail.recycle(); tag.recycle() }
    }
}
