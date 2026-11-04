package nl.spoonpaco.ninegrid.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintDao {
    @Upsert
    suspend fun upsert(prints: List<CardPrint>)

    @Query("SELECT * FROM prints WHERE id = :id")
    suspend fun get(id: String): CardPrint?

    @Query("SELECT * FROM prints WHERE id = :id")
    fun observe(id: String): Flow<CardPrint?>

    @Query("SELECT * FROM prints")
    suspend fun all(): List<CardPrint>

    @Query("SELECT * FROM prints WHERE id IN (SELECT DISTINCT printId FROM entries)")
    suspend fun owned(): List<CardPrint>

    @Query("DELETE FROM prints WHERE id NOT IN (SELECT DISTINCT printId FROM entries)")
    suspend fun deleteUnused()

    @Query("DELETE FROM prints")
    suspend fun deleteAll()
}

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Upsert
    suspend fun upsert(entries: List<Entry>)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("DELETE FROM entries WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): Entry?

    @Query("SELECT * FROM entries WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<Entry>

    @Query("SELECT * FROM entries")
    suspend fun all(): List<Entry>

    @Query("SELECT * FROM entries WHERE deckId = :deckId")
    suspend fun forDeck(deckId: Long): List<Entry>

    @Query("SELECT COUNT(*) FROM entries WHERE printId = :printId")
    suspend fun countForPrint(printId: String): Int

    @Query(
        "SELECT * FROM entries WHERE deckId IS NULL AND binder = :binder AND page = :page AND slot = :slot"
    )
    suspend fun atSlot(binder: String, page: Int, slot: Int): List<Entry>

    @Query("SELECT DISTINCT binder FROM entries WHERE binder IS NOT NULL ORDER BY binder")
    fun binders(): Flow<List<String>>

    @Query("SELECT MAX(page) FROM entries WHERE binder = :binder")
    suspend fun maxPage(binder: String): Int?

    @Transaction
    @Query("SELECT * FROM entries")
    fun observeAll(): Flow<List<EntryWithPrint>>

    @Transaction
    @Query("SELECT * FROM entries WHERE printId = :printId ORDER BY deckId, binder, page, slot")
    fun observeForPrint(printId: String): Flow<List<EntryWithPrint>>

    @Transaction
    @Query("SELECT * FROM entries WHERE deckId = :deckId")
    fun observeForDeck(deckId: Long): Flow<List<EntryWithPrint>>

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}

@Dao
interface DeckDao {
    @Insert
    suspend fun insert(deck: Deck): Long

    @Upsert
    suspend fun upsert(decks: List<Deck>)

    @Query("DELETE FROM decks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun get(id: Long): Deck?

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observe(id: Long): Flow<Deck?>

    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Deck>>

    @Query("SELECT * FROM decks")
    suspend fun all(): List<Deck>

    @Query("DELETE FROM decks")
    suspend fun deleteAll()
}

@Dao
interface ChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: ChangeRecord): Long

    @Query("SELECT * FROM changes ORDER BY timestamp DESC LIMIT 500")
    fun observeAll(): Flow<List<ChangeRecord>>

    @Query("SELECT * FROM changes WHERE reverted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestActive(): ChangeRecord?

    @Query("UPDATE changes SET reverted = 1 WHERE id = :id")
    suspend fun markReverted(id: Long)

    @Query("DELETE FROM changes")
    suspend fun deleteAll()
}

@Dao
interface SnapshotDao {
    @Upsert
    suspend fun upsert(items: List<Snapshot>)

    @Query("SELECT * FROM snapshots ORDER BY day")
    fun observeAll(): Flow<List<Snapshot>>

    @Query("SELECT * FROM snapshots")
    suspend fun all(): List<Snapshot>

    @Query("DELETE FROM snapshots WHERE day = :day")
    suspend fun deleteDay(day: String)

    @Query("DELETE FROM snapshots")
    suspend fun deleteAll()
}

@Dao
interface MomentDao {
    @Upsert
    suspend fun upsert(items: List<Moment>)

    @Query("SELECT * FROM moments ORDER BY ts")
    fun observeAll(): Flow<List<Moment>>

    @Query("DELETE FROM moments WHERE ts > :after")
    suspend fun deleteAfter(after: Long)

    @Query("DELETE FROM moments WHERE ts < :before")
    suspend fun deleteBefore(before: Long)
}
