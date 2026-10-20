package fr.centaurix.app

import org.junit.Assert.*
import org.junit.Test

class PuffLedgerTest {
    @Test fun firstReadIsBaselineNotDailyTotal() {
        val tracker=PuffLedger()
        val state=tracker.accept("2026-10-20",120,450,1000L)
        assertEquals(0L,state.dailyPuffs)
        assertEquals(0L,tracker.unassignedPuffs)
        assertEquals(0L,tracker.forDay("2026-10-20").puffs)
    }
    @Test fun consecutiveSameDayReadsAccumulateOnlyChanges() {
        val t=PuffLedger()
        t.accept("2026-10-20",100,30,1000L)
        t.accept("2026-10-20",103,41,2000L)
        t.accept("2026-10-20",103,41,3000L)
        t.accept("2026-10-20",107,55,4000L)
        assertEquals(7L,t.forDay("2026-10-20").puffs)
        assertEquals(25L,t.forDay("2026-10-20").time)
    }
    @Test fun reconnectSameDayCanRecoverDelta() {
        val t=PuffLedger()
        t.accept("2026-10-20",37,20,1000)
        val restored=PuffLedger().also{it.load(t.history(),t.baseline)}
        restored.accept("2026-10-20",44,28,9000)
        assertEquals(7L,restored.forDay("2026-10-20").puffs)
    }
    @Test fun changesAcrossMidnightRemainUnassigned() {
        val t=PuffLedger()
        t.accept("2026-10-20",80,30,1000L)
        t.accept("2026-10-21",90,40,9000L)
        assertEquals(10L,t.unassignedPuffs)
        assertEquals(0L,t.forDay("2026-10-21").puffs)
        t.accept("2026-10-21",94,42,10000L)
        assertEquals(4L,t.forDay("2026-10-21").puffs)
        assertEquals(0L,t.forDay("2026-10-20").puffs)
    }
    @Test fun counterResetDoesNotProduceNegativeOrFakeGrowth() {
        val t=PuffLedger()
        t.accept("2026-10-20",400,1000,1000L)
        t.accept("2026-10-20",5,20,2000L)
        t.accept("2026-10-20",8,25,3000L)
        assertEquals(3L,t.forDay("2026-10-20").puffs)
        assertTrue(t.history().all{it.puffs>=0})
    }
    @Test fun movingClockBackwardsCannotDuplicateHistoricalPuffs() {
        val t=PuffLedger()
        t.accept("2026-10-20",25,20,2000L)
        t.accept("2026-10-20",30,35,1000L)
        assertEquals(0L,t.forDay("2026-10-20").puffs)
        t.accept("2026-10-20",31,36,3000L)
        assertEquals(1L,t.forDay("2026-10-20").puffs)
    }
    @Test fun persistedStateLoadsAndClearWorks() {
        val t=PuffLedger()
        t.accept("2026-10-20",12,20,1000L)
        t.accept("2026-10-20",15,27,2000L)
        val loaded=PuffLedger()
        loaded.load(t.history(),t.baseline,t.unassignedPuffs,t.unassignedTime)
        assertEquals(3L,loaded.forDay("2026-10-20").puffs)
        loaded.clear()
        assertTrue(loaded.history().isEmpty())
        assertNull(loaded.baseline)
    }
}
