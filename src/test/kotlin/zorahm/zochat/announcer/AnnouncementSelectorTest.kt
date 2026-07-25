package zorahm.zochat.announcer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AnnouncementSelectorTest {

    @Test
    fun sequentialCyclesInOrderAndWraps() {
        val selector = AnnouncementSelector(listOf("a", "b", "c"), SelectionType.SEQUENTIAL)
        assertEquals("a", selector.next())
        assertEquals("b", selector.next())
        assertEquals("c", selector.next())
        assertEquals("a", selector.next())
    }

    @Test
    fun emptyListReturnsNull() {
        assertNull(AnnouncementSelector(emptyList(), SelectionType.SEQUENTIAL).next())
        assertNull(AnnouncementSelector(emptyList(), SelectionType.RANDOM).next())
    }

    @Test
    fun randomFollowsInjectedRandom() {
        // A seeded Random is deterministic, so we can assert the exact sequence of picks.
        val ids = listOf("a", "b", "c")
        val selector = AnnouncementSelector(ids, SelectionType.RANDOM, Random(0))
        val expected = Random(0)
        repeat(5) { assertEquals(ids[expected.nextInt(ids.size)], selector.next()) }
    }

    @Test
    fun unknownSelectionTypeFallsBackToSequential() {
        assertEquals(SelectionType.SEQUENTIAL, SelectionType.fromString("nonsense"))
        assertEquals(SelectionType.SEQUENTIAL, SelectionType.fromString(null))
        assertEquals(SelectionType.RANDOM, SelectionType.fromString("random"))
        assertEquals(SelectionType.SEQUENTIAL, SelectionType.fromString("SEQUENTIAL"))
    }
}
