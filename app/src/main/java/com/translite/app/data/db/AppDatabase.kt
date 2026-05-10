package com.translite.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.translite.app.data.db.entity.TranslationEntity

@Database(
    entities = [TranslationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE translations ADD COLUMN mode TEXT NOT NULL DEFAULT 'standard'")
                db.execSQL("ALTER TABLE translations ADD COLUMN analysis_data TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema changed, recreate table
                db.execSQL("CREATE TABLE IF NOT EXISTS translations_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, originalText TEXT NOT NULL, translatedText TEXT NOT NULL, sourceLangCode TEXT NOT NULL, targetLangCode TEXT NOT NULL, timestamp INTEGER NOT NULL, isFavorite INTEGER NOT NULL, isOffline INTEGER NOT NULL, mode TEXT NOT NULL DEFAULT 'standard', analysisData TEXT DEFAULT NULL)")
                db.execSQL("INSERT INTO translations_new SELECT id, originalText, translatedText, sourceLangCode, targetLangCode, timestamp, isFavorite, isOffline, mode, analysisData FROM translations")
                db.execSQL("DROP TABLE translations")
                db.execSQL("ALTER TABLE translations_new RENAME TO translations")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "translite.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
