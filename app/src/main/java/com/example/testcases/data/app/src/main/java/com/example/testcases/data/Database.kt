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
    @Query("SELECT * FROM test_cases ORDER BY number ASC, id ASC")
    fun observeAll(): Flow<List<TestCase>>

    @Query("SELECT * FROM test_cases ORDER BY number ASC, id ASC")
    suspend fun getAll(): List<TestCase>

    @Query("DELETE FROM test_cases")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(number), 0) FROM test_cases")
    suspend fun maxNumber(): Int

    /** Освобождает место под номер [from]: все номера от него и выше сдвигаются на единицу. */
    @Query("UPDATE test_cases SET number = number + 1 WHERE number >= :from")
    suspend fun shiftFrom(from: Int)

    @Query("UPDATE test_cases SET number = :number WHERE id = :id")
    suspend fun setNumber(id: Long, number: Int)

    /** Ставит один статус сразу нескольким кейсам (status — имя значения Status). */
    @Query("UPDATE test_cases SET status = :status, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: String, now: Long)

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

    @Query("SELECT bugScreens FROM test_cases WHERE bugScreens != ''")
    suspend fun allBugScreens(): List<String>

    @Query("DELETE FROM test_cases WHERE sectionId = :sectionId")
    suspend fun deleteBySection(sectionId: Long)

    @Query("UPDATE test_cases SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun detachFromSection(sectionId: Long)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Section>>

    @Query("SELECT * FROM sections ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Section>

    @Query("DELETE FROM sections")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(sections: List<Section>)

    @Query("UPDATE sections SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

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

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `hasBug` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `bugDescription` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `bugScreens` TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sections` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        // Сохраняем нынешний порядок: раньше разделы шли по номеру
        db.execSQL("UPDATE `sections` SET `sortOrder` = `id`")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `number` INTEGER NOT NULL DEFAULT 0")
        // Нумеруем подряд по порядку создания: заодно закрываются нынешние пропуски
        db.execSQL(
            "UPDATE `test_cases` SET `number` = " +
                "(SELECT COUNT(*) FROM `test_cases` AS t WHERE t.`id` <= `test_cases`.`id`)"
        )
    }
}

@Database(entities = [TestCase::class, Section::class], version = 5, exportSchema = false)
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
