package nl.spoonpaco.ninegrid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardPrint::class, Entry::class, Deck::class, ChangeRecord::class, Snapshot::class, Moment::class, PricePoint::class, UndoPart::class, Friend::class, FriendPackage::class, FriendCard::class,
        WishItem::class, Booster::class, BoosterCard::class, BoosterBox::class],
    version = 10,
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
    abstract fun friends(): FriendDao
    abstract fun wishlist(): WishDao
    abstract fun boosters(): BoosterDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ninegrid.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()

        /** Versie 10: verlanglijst (met prijsalarm) en geopende boosters. */
        val V10_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `wishlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `oracleId` TEXT NOT NULL, `printId` TEXT NOT NULL, `anyPrint` INTEGER NOT NULL, `foilOnly` INTEGER NOT NULL, `wanted` INTEGER NOT NULL, `got` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `alarmPrice` REAL, `alarmCurrency` TEXT NOT NULL, `alarmArmed` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_wishlist_name` ON `wishlist` (`name`)",
            "CREATE TABLE IF NOT EXISTS `boosters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `openedAt` INTEGER NOT NULL, `setCode` TEXT NOT NULL, `type` TEXT NOT NULL, `paid` REAL NOT NULL, `currency` TEXT NOT NULL, `boxId` INTEGER, `valueAtOpen` REAL NOT NULL, `valueNoBulkAtOpen` REAL NOT NULL, `changeId` INTEGER)",
            "CREATE TABLE IF NOT EXISTS `booster_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `boosterId` INTEGER NOT NULL, `printId` TEXT NOT NULL, `finish` TEXT NOT NULL, `quantity` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_booster_cards_boosterId` ON `booster_cards` (`boosterId`)",
            "CREATE TABLE IF NOT EXISTS `booster_boxes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `setCode` TEXT NOT NULL, `type` TEXT NOT NULL, `size` INTEGER NOT NULL, `price` REAL NOT NULL, `currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) { V10_SQL.forEach { db.execSQL(it) } }
        }

        /** De tabellen en indexen van versie 9; ook gebruikt in de test die ze vergelijkt met het schema van Room. */
        val FRIEND_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `friends` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `fromOtherApp` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `friend_packages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `friendId` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `kind` TEXT NOT NULL, `format` TEXT, `receivedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_friend_packages_friendId` ON `friend_packages` (`friendId`)",
            "CREATE TABLE IF NOT EXISTS `friend_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageId` INTEGER NOT NULL, `printId` TEXT NOT NULL, `finish` TEXT NOT NULL, `language` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `section` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_friend_cards_packageId` ON `friend_cards` (`packageId`)",
            "CREATE INDEX IF NOT EXISTS `index_friend_cards_printId` ON `friend_cards` (`printId`)",
        )

        /** Versie 9: collecties van anderen (alleen bekijken en ruilen). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) { FRIEND_SQL.forEach { db.execSQL(it) } }
        }

        /** Versie 8: een deck onthoudt wanneer het voor het laatst is gewijzigd (begint op de aanmaakdatum). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `decks` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `decks` SET `updatedAt` = `createdAt`")
            }
        }

        /** Versie 7: koersverschil per dagstand, en welke mana een kaart maakt (gevuld bij de volgende prijsupdate). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `snapshots` ADD COLUMN `gainEur` REAL")
                db.execSQL("ALTER TABLE `snapshots` ADD COLUMN `gainUsd` REAL")
                db.execSQL("ALTER TABLE `prints` ADD COLUMN `producedMana` TEXT NOT NULL DEFAULT ''")
            }
        }

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
