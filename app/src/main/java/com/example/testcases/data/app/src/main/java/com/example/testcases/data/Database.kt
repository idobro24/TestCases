package com.example.testcases.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TestCaseDao {
    @Query("SELECT * FROM test_cases ORDER BY id ASC")
    fun observeAll(): Flow<List<TestCase>>

    @Query("SELECT * FROM test_cases WHERE id = :id")
    suspend fun getById(id: Long): TestCase?

    @Insert
    suspend fun insert(testCase: TestCase): Long

    @Insert
    suspend fun insertAll(testCases: List<TestCase>)

    @Update
    suspend fun update(testCase: TestCase)

    @Delete
    suspend fun delete(testCase: TestCase)

    @Query("DELETE FROM test_cases WHERE sectionId = :sectionId")
    suspend fun deleteBySection(sectionId: Long)

    @Query("UPDATE test_cases SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun detachFromSection(sectionId: Long)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY id ASC")
    fun observeAll(): Flow<List<Section>>

    @Query("SELECT * FROM sections ORDER BY id ASC")
    suspend fun getAll(): List<Section>

    @Insert
    suspend fun insert(section: Section): Long

    @Update
    suspend fun update(section: Section)

    @Delete
    suspend fun delete(section: Section)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sections` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `sectionId` INTEGER")
    }
}

@Database(entities = [TestCase::class, Section::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TestCaseDao
    abstract fun sections(): SectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "testcases.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
