package zorahm.zochat.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.math.abs

class StorageTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var db: Database

    @BeforeEach
    fun open() {
        db = Database.sqlite(dir.resolve("chat.db").toFile(), Logger.getLogger("zoChat-test"))
        db.init()
    }

    @AfterEach
    fun close() {
        db.close()
    }

    // Repository callbacks fire on the DB thread; block the test until one arrives.
    private fun <T> await(call: ((T) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        call {
            result = it
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "DB callback never fired")
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    // SQLite's CURRENT_TIMESTAMP is UTC text, but sqlite-jdbc reads that text back as local time —
    // the shown send time was off by the server's UTC offset (3 h on a Moscow host).
    @Test
    fun offlineMessageIsDeliveredWithItsSendTime() {
        val repo = OfflineMessageRepository(db).also { it.createTable() }
        val receiver = UUID.randomUUID()
        val before = System.currentTimeMillis()
        repo.save(UUID.randomUUID(), receiver, "hi")

        val drained = await<List<OfflineMessageRepository.OfflineMessage>> { repo.drainFor(receiver, it) }

        assertEquals(listOf("hi"), drained.map { it.message })
        assertTrue(abs(drained[0].sentAtMillis - before) < 5_000, "send time off by ${drained[0].sentAtMillis - before} ms")
    }

    // The player can quit while the drain is in flight; the drained rows must be put back intact.
    @Test
    fun restoredMessagesAreDeliveredAgainWithTheirOriginalTime() {
        val repo = OfflineMessageRepository(db).also { it.createTable() }
        val receiver = UUID.randomUUID()
        repo.save(UUID.randomUUID(), receiver, "hi")
        val first = await<List<OfflineMessageRepository.OfflineMessage>> { repo.drainFor(receiver, it) }

        repo.restore(receiver, first)
        val again = await<List<OfflineMessageRepository.OfflineMessage>> { repo.drainFor(receiver, it) }

        assertEquals(first, again)
    }

    @Test
    fun drainEmptiesTheQueue() {
        val repo = OfflineMessageRepository(db).also { it.createTable() }
        val receiver = UUID.randomUUID()
        repo.save(UUID.randomUUID(), receiver, "hi")
        await<List<OfflineMessageRepository.OfflineMessage>> { repo.drainFor(receiver, it) }

        val second = await<List<OfflineMessageRepository.OfflineMessage>> { repo.drainFor(receiver, it) }

        assertEquals(emptyList<OfflineMessageRepository.OfflineMessage>(), second)
    }

    // timestamp has 1-second resolution, so ordering by it shuffled messages sent within a second.
    @Test
    fun chatLogListsNewestFirst() {
        val repo = ChatLogRepository(db).also { it.createTable() }
        val player = UUID.randomUUID()
        repo.log(player, "a")
        repo.log(player, "b")
        repo.log(player, "c")

        val recent = await<List<String>?> { repo.recentFor(player, it) }

        assertEquals(listOf("c", "b", "a"), recent)
    }
}
