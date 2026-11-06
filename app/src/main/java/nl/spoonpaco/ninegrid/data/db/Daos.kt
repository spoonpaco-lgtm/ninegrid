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

    /** Prints van jezelf én van ontvangen collecties: die krijgen allemaal verse prijzen. */
    @Query("SELECT * FROM prints WHERE id IN (SELECT DISTINCT printId FROM entries UNION SELECT DISTINCT printId FROM friend_cards)")
    suspend fun ownedOrShared(): List<CardPrint>

    @Query("SELECT id FROM prints")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM prints WHERE id NOT IN (SELECT DISTINCT printId FROM entries UNION SELECT DISTINCT printId FROM friend_cards)")
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

    /** Alleen de exemplaren van deze prints: voor het samenvoegen van stapels, zonder alles in te lezen. */
    @Query("SELECT DISTINCT deckId FROM entries WHERE id IN (:ids) AND deckId IS NOT NULL")
    suspend fun deckIdsOf(ids: List<Long>): List<Long>

    @Query("SELECT * FROM entries WHERE printId IN (:ids)")
    suspend fun forPrints(ids: List<String>): List<Entry>

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

    @Query("UPDATE decks SET updatedAt = :at WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, at: Long)
}

@Dao
interface ChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: ChangeRecord): Long

    /** Voor de lijst alleen de samenvatting: de ongedaan-maak-gegevens kunnen groot zijn. */
    @Query("SELECT id, timestamp, summary, '' AS undoJson, reverted FROM changes ORDER BY timestamp DESC LIMIT 500")
    fun observeAll(): Flow<List<ChangeRecord>>

    @Insert
    suspend fun insertParts(parts: List<UndoPart>)

    @Query("SELECT * FROM undo_parts WHERE changeId = :id ORDER BY idx")
    suspend fun parts(id: Long): List<UndoPart>

    @Query("DELETE FROM undo_parts")
    suspend fun deleteAllParts()

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

    /** Alleen de lijnen die in de grafiek staan, plus het totaal (voor het verschil bovenaan). */
    @Query("SELECT * FROM snapshots WHERE `key` IN (:keys) OR `key` = 'total' ORDER BY day")
    fun observeKeys(keys: List<String>): Flow<List<Snapshot>>

    /** Welke lijnen er ooit zijn geweest, met hun laatste naam (voor de keuzelijst). */
    @Query("SELECT s.* FROM snapshots s JOIN (SELECT `key` AS k, MAX(day) AS d FROM snapshots GROUP BY `key`) m ON s.`key` = m.k AND s.day = m.d")
    fun observeLatestPerKey(): Flow<List<Snapshot>>

    @Query("SELECT * FROM snapshots")
    suspend fun all(): List<Snapshot>

    @Query("DELETE FROM snapshots WHERE day = :day")
    suspend fun deleteDay(day: String)

    @Query("DELETE FROM snapshots")
    suspend fun deleteAll()
}

@Dao
interface MomentDao {
    @Query("DELETE FROM moments")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(items: List<Moment>)

    @Query("SELECT * FROM moments ORDER BY ts")
    fun observeAll(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE `key` IN (:keys) AND ts >= :from ORDER BY ts")
    fun observeKeys(keys: List<String>, from: Long): Flow<List<Moment>>

    @Query("DELETE FROM moments WHERE `key` LIKE 'set:%'")
    suspend fun deleteSetMoments()

    @Query("SELECT * FROM moments")
    suspend fun all(): List<Moment>

    @Query("DELETE FROM moments WHERE ts > :after")
    suspend fun deleteAfter(after: Long)

    @Query("DELETE FROM moments WHERE ts < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface PriceDao {
    @Upsert
    suspend fun upsert(items: List<PricePoint>)

    /** Per print de laatst bekende prijs op of vóór [day]. */
    @Query("SELECT p.* FROM price_history p JOIN (SELECT printId AS pid, MAX(day) AS d FROM price_history WHERE day <= :day GROUP BY printId) m ON p.printId = m.pid AND p.day = m.d")
    suspend fun asOf(day: String): List<PricePoint>

    @Query("SELECT * FROM price_history")
    suspend fun all(): List<PricePoint>

    @Query("SELECT DISTINCT printId FROM price_history")
    suspend fun knownPrintIds(): List<String>

    @Query("SELECT MIN(day) FROM price_history")
    suspend fun firstDay(): String?

    /** Ouder dan een maand: alleen de eerste meting van elke maand bewaren. */
    @Query("DELETE FROM price_history WHERE day < :cutoff AND day NOT IN (SELECT MIN(q.day) FROM price_history q WHERE q.printId = price_history.printId GROUP BY substr(q.day, 1, 7))")
    suspend fun thin(cutoff: String)

    @Query("DELETE FROM price_history")
    suspend fun deleteAll()
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY name COLLATE NOCASE")
    fun observeFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friend_packages ORDER BY kind, label COLLATE NOCASE")
    fun observePackages(): Flow<List<FriendPackage>>

    @Transaction
    @Query("SELECT * FROM friend_cards")
    fun observeCards(): Flow<List<FriendCardWithPrint>>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun friend(id: String): Friend?

    @androidx.room.Upsert
    suspend fun upsertFriend(f: Friend)

    @Query("SELECT * FROM friend_packages WHERE friendId = :friendId AND `key` = :key")
    suspend fun packageByKey(friendId: String, key: String): FriendPackage?

    @Insert
    suspend fun insertPackage(p: FriendPackage): Long

    @Insert
    suspend fun insertCards(c: List<FriendCard>)

    @Query("DELETE FROM friend_cards WHERE packageId = :packageId")
    suspend fun deleteCardsOf(packageId: Long)

    @Query("DELETE FROM friend_packages WHERE id = :id")
    suspend fun deletePackageRow(id: Long)

    @Query("SELECT id FROM friend_packages WHERE friendId = :friendId")
    suspend fun packageIdsOf(friendId: String): List<Long>

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriendRow(id: String)

    @Query("UPDATE friends SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Transaction
    suspend fun replacePackage(p: FriendPackage, cards: List<FriendCard>) {
        packageByKey(p.friendId, p.key)?.let { old -> deleteCardsOf(old.id); deletePackageRow(old.id) }
        val id = insertPackage(p)
        insertCards(cards.map { it.copy(packageId = id) })
    }

    @Transaction
    suspend fun deletePackage(id: Long) { deleteCardsOf(id); deletePackageRow(id) }

    @Transaction
    suspend fun deleteFriend(id: String) {
        packageIdsOf(id).forEach { deleteCardsOf(it); deletePackageRow(it) }
        deleteFriendRow(id)
    }
}
