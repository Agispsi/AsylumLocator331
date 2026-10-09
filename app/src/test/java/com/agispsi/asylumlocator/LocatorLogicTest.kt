package com.agispsi.asylumlocator

import org.junit.Assert.*
import org.junit.Test

class LocatorLogicTest {
    @Test fun matchesAllRequiredNames() {
        listOf("[GNA] GoneAway","[ABC] xGONEAWAYx","[OUT] goneaway123","[XYZ] MyGoneAwayPlayer").forEach { assertTrue(it,LocatorLogic.matches(it,"goneaway")) }
    }
    @Test fun allianceOnlyNeverMatches() {
        assertFalse(LocatorLogic.matches("[goneaway] SomeoneElse","goneaway"))
        assertFalse(LocatorLogic.matches("Someone[GoNeAwAy]Else","goneaway"))
        assertFalse(LocatorLogic.matches("[goneaway[ABC]]Someone","goneaway"))
        assertFalse(LocatorLogic.matches("[GNA] GoneAway","[GNA]"))
    }
    @Test fun repeatedAndChangedTagsAreIgnored() {
        assertEquals("GoneAway",LocatorLogic.cleanName(" [OUT] [GNA] GoneAway "))
        assertTrue(LocatorLogic.matches("[NEW] GoneAway","[OLD] goneaway"))
    }
    @Test fun levelMustBeSanctuaryAndUnambiguous() {
        assertEquals(22,LocatorLogic.identity("[OUT] Reader","Sanctuary Lv. 22")!!.level)
        assertEquals(22,LocatorLogic.identity("Reader","22")!!.level)
        assertNull(LocatorLogic.identity("Reader","VIP 9"))
        assertNull(LocatorLogic.identity("Reader","22 9"))
        assertNull(LocatorLogic.identity("Reader\nOther","22"))
        assertNull(LocatorLogic.identity("[OUT Reader","22"))
    }
    @Test fun coordinateExtractionRequiresExplicitServerAndAxes() {
        assertEquals(Coordinates(331,123,456),LocatorLogic.coordinates("Add Tag\n#331 X:123 Y:456"))
        assertEquals(Coordinates(331,0,0),LocatorLogic.coordinates("#331\nX:0\nY:0"))
        assertNull(LocatorLogic.coordinates("X:123 Y:456"))
        assertNull(LocatorLogic.coordinates("#331 X:12O Y:456"))
        assertNull(LocatorLogic.coordinates("#331 X:123 Y:45O"))
        assertNull(LocatorLogic.coordinates("#331 X:123 Y:456.7"))
        assertNull(LocatorLogic.coordinates("22 #331 X:123 Y:456"))
        assertNull(LocatorLogic.coordinates("#331 X:123 Y:456 #331 X:124 Y:456"))
    }
    @Test fun wrongLevelIsRejected() {
        val spec=SearchSpec("goneaway",22)
        assertTrue(spec.matches(LocatorLogic.identity("[A] GoneAway","22")!!))
        assertFalse(spec.matches(LocatorLogic.identity("[A] GoneAway","21")!!))
    }
    @Test fun coordinatesCannotBePairedWithAnotherPlayer() {
        val p=LocatorLogic.identity("[A] GoneAway","22")!!
        val q=LocatorLogic.identity("[A] SomeoneElse","22")!!
        val c=Coordinates(331,123,456)
        val spec=SearchSpec("goneaway",22)
        assertTrue(LocatorLogic.verifiedChain(p,p,p,c,c,spec))
        assertFalse(LocatorLogic.verifiedChain(p,p,q,c,c,spec))
        assertFalse(LocatorLogic.verifiedChain(p,q,p,c,c,spec))
        assertFalse(LocatorLogic.verifiedChain(p,p,p,c,Coordinates(331,124,456),spec))
        assertFalse(LocatorLogic.verifiedChain(p,p,p,Coordinates(330,123,456),Coordinates(330,123,456),spec))
    }
    @Test fun duplicateKeysKeepDifferentPlayersAndLocationsSeparate() {
        val p=LocatorLogic.identity("[A] GoneAway","22")!!
        val renamedTag=LocatorLogic.identity("[B] goneaway","22")!!
        val q=LocatorLogic.identity("[A] xGoneAwayx","22")!!
        val c=Coordinates(331,123,456)
        assertEquals(LocatorLogic.observationKey(p,c),LocatorLogic.observationKey(renamedTag,c))
        assertNotEquals(LocatorLogic.observationKey(p,c),LocatorLogic.observationKey(q,c))
        assertNotEquals(LocatorLogic.observationKey(p,c),LocatorLogic.observationKey(p,Coordinates(331,124,456)))
    }
    @Test fun sweepIsBoundedAndSerpentine() {
        assertEquals(listOf("left","left","up","right","right",null),(0..5).map { LocatorLogic.nextMove(it,2,3) })
        assertNull(LocatorLogic.nextMove(0,1,1))
        assertEquals("up",LocatorLogic.nextMove(0,2,1))
    }
    @Test fun zeroResultsNeverClaimFullCoverage() {
        val status=LocatorLogic.coverage(0,25,0,"Cancelled")
        assertTrue(status.contains("0/25")); assertTrue(status.contains("Search incomplete")); assertFalse(status.contains("no match",true))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsAllianceOnlyQuery() { SearchSpec("[goneaway]",22) }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnboundedSweep() { SearchSpec("a",null,331,500,500) }
}
