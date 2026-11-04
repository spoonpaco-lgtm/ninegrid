package nl.spoonpaco.ninegrid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardPrint::class, Entry::class, Deck::class, ChangeRecord::class, Snapshot::class, Moment::class, PricePoint::class, UndoPart::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prints(): PrintDao
    abstract fun entries(): EntryDao
    abstract fun decks(): DeckDao
    abstract fun changes(): ChangeDao
    abstract fun snapshots(): SnapshotDao
    abstract fun moments(): MomentDao
    abstract fun prices(): PriceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ninegrid.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        /** Versie 6: grote ongedaan-maak-gegevens in stukken, en een index op de prijzenhistorie per dag. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `undo_parts` (`changeId` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`changeId`, `idx`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_history_day` ON `price_history` (`day`)")
            }
        }

        /** Versie 5: regeltekst per print. Bestaande kaarten krijgen hem bij de volgende prijsupdate. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `prints` ADD COLUMN `oracleText` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Versie 4: prijzen per print per dag, voor het koersverschil. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `price_history` (`printId` TEXT NOT NULL, `day` TEXT NOT NULL, `eur` REAL, `eurFoil` REAL, " +
                        "`usd` REAL, `usdFoil` REAL, `usdEtched` REAL, PRIMARY KEY(`printId`, `day`))"
                )
            }
        }

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
