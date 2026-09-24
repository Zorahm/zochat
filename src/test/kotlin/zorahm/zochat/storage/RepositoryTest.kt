package zorahm.zochat.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zorahm.zochat.Fakes
import zorahm.zochat.storage.OfflineMessageRepository.SaveResult
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/** Runs both repositories against a real SQLite file (sqlite-jdbc is test-only; Paper ships it at runtime). */
class RepositoryTest {

    @TempDir
    lateinit var dir: File

    private lateinit var db: Database
    private lateinit var chatLog: ChatLogRepository
    private lateinit var offline: OfflineMessageRepository

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun open() {
        db = Database.sqlite(Fakes.plugin(dir))
        db.init()
        chatLog = ChatLogRepository(db).also { it.createTable() }
        offline = OfflineMessageRepository(db).also { it.createTable() }
    }

    @AfterEach
    fun close() = db.close()

    // Repositories answer on the single DB thread; queued work before a read has finished by the time it answers.
    private fun <T> await(call: (Consumer<T>) -> Unit): T {
        val future = CompletableFuture<T>()
        call(Consumer { future.complete(it) })
        return future.get(10, TimeUnit.SECONDS)
    }

    private fun pending(receiver: UUID) = await<List<OfflineMessageRepository.OfflineMessage>> { offline.pendingFor(receiver, it) }

    @Test
    fun offlineMessagesSurviveReadingUntilDeleted() {
        assertEquals(SaveResult.SAVED, await<SaveResult> { offline.save(alice, bob, "hi", 0, it) })

        val first = pending(bob)
        assertEquals(listOf("hi"), first.map { it.message() })
        // Reading alone must not remove them: the player might leave before delivery.
        assertEquals(1, pending(bob).size)

        offline.delete(first)
        assertTrue(pending(bob).isEmpty())
    }

    @Test
    fun deleteKeepsMessagesThatArrivedAfterTheRead() {
        await<SaveResult> { offline.save(alice, bob, "first", 0, it) }
        val read = pending(bob)
        await<SaveResult> { offline.save(alice, bob, "second", 0, it) }

        offline.delete(read)

        assertEquals(listOf("second"), pending(bob).map { it.message() })
    }

    @Test
    fun timestampIsTheRealSendTime() {
        val before = System.currentTimeMillis()
        await<SaveResult> { offline.save(alice, bob, "hi", 0, it) }
        val stored = pending(bob).single().timestamp().time
        // SQLite's UTC CURRENT_TIMESTAMP read back as local time was off by the whole UTC offset.
        assertTrue(stored in (before - 2_000)..(System.currentTimeMillis() + 2_000), "stored=$stored before=$before")
    }

    @Test
    fun inboxLimitRejectsOverflow() {
        repeat(2) { i -> assertEquals(SaveResult.SAVED, await<SaveResult> { offline.save(alice, bob, "m$i", 2, it) }) }
        assertEquals(SaveResult.INBOX_FULL, await<SaveResult> { offline.save(alice, bob, "m3", 2, it) })
        assertEquals(2, pending(bob).size)
        // The limit is per receiver.
        assertEquals(SaveResult.SAVED, await<SaveResult> { offline.save(bob, alice, "back", 2, it) })
    }

    @Test
    fun chatLogReturnsNewestFirstEvenWithinOneSecond() {
        (1..12).forEach { chatLog.log(alice, "m$it") }
        val recent = await<List<String>> { chatLog.recentFor(alice, it) }
        assertEquals((12 downTo 3).map { "m$it" }, recent)
    }

    @Test
    fun purgeDropsOnlyEntriesPastRetention() {
        db.runAsync {
            db.conn().prepareStatement(
                "INSERT INTO chat_logs (player_uuid, message, timestamp) VALUES (?, 'ancient', '2000-01-01 00:00:00')"
            ).use { st ->
                st.setString(1, alice.toString())
                st.executeUpdate()
            }
        }
        chatLog.log(alice, "fresh")

        chatLog.purgeOlderThan(0) // 0 = keep forever
        assertEquals(listOf("fresh", "ancient"), await<List<String>> { chatLog.recentFor(alice, it) })

        chatLog.purgeOlderThan(30)
        assertEquals(listOf("fresh"), await<List<String>> { chatLog.recentFor(alice, it) })
    }
}
