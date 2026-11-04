package nl.spoonpaco.ninegrid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardPrint::class, Entry::class, Deck::class, ChangeRecord::class, Snapshot::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prints(): PrintDao
    abstract fun entries(): EntryDao
    abstract fun decks(): DeckDao
    abstract fun changes(): ChangeDao
    abstract fun snapshots(): SnapshotDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ninegrid.db")
                .addMigrations(MIGRATION_1_2)
                .build()

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
