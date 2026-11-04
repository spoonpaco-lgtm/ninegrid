package nl.spoonpaco.ninegrid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardPrint::class, Entry::class, Deck::class, ChangeRecord::class, Snapshot::class, Moment::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prints(): PrintDao
    abstract fun entries(): EntryDao
    abstract fun decks(): DeckDao
    abstract fun changes(): ChangeDao
    abstract fun snapshots(): SnapshotDao
    abstract fun moments(): MomentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ninegrid.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** Versie 3: momenten binnen een dag voor de grafiek (1D, 1W, 1M). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `moments` (`ts` INTEGER NOT NULL, `key` TEXT NOT NULL, `eur` REAL NOT NULL, " +
                        "`usd` REAL NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`ts`, `key`))"
                )
            }
        }

        /** Versie 2: tabel voor de momentopnamen van de grafiek. Bestaande kaarten en decks blijven staan. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `snapshots` (`day` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                        "`eur` REAL NOT NULL, `usd` REAL NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`day`, `key`))"
                )
            }
        }
    }
}
