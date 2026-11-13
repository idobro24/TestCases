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
    @Query("SELECT * FROM test_cases WHERE projectId = :projectId ORDER BY number ASC, id ASC")
    fun observeByProject(projectId: Long): Flow<List<TestCase>>

    @Query("SELECT * FROM test_cases WHERE projectId = :projectId ORDER BY number ASC, id ASC")
    suspend fun getByProject(projectId: Long): List<TestCase>

    /** Кейсы всех проектов — для резервной копии. */
    @Query("SELECT * FROM test_cases ORDER BY projectId ASC, number ASC, id ASC")
    suspend fun getAll(): List<TestCase>

    @Query("DELETE FROM test_cases")
    suspend fun deleteAll()

    @Query("DELETE FROM test_cases WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    @Query("SELECT COALESCE(MAX(number), 0) FROM test_cases WHERE projectId = :projectId")
    suspend fun maxNumber(projectId: Long): Int

    /** Освобождает место под номер [from] в проекте: все номера от него и выше сдвигаются на единицу. */
    @Query("UPDATE test_cases SET number = number + 1 WHERE projectId = :projectId AND number >= :from")
    suspend fun shiftFrom(projectId: Long, from: Int)

    @Query("UPDATE test_cases SET number = :number WHERE id = :id")
    suspend fun setNumber(id: Long, number: Int)

    @Query("UPDATE test_cases SET hasBug = :hasBug, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setBug(ids: List<Long>, hasBug: Boolean, now: Long)

    @Query("UPDATE test_cases SET sectionId = :sectionId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setSection(ids: List<Long>, sectionId: Long?, now: Long)

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
    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    fun observeByProject(projectId: Long): Flow<List<Section>>

    @Query("SELECT * FROM sections WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByProject(projectId: Long): List<Section>

    /** Разделы всех проектов — для резервной копии. */
    @Query("SELECT * FROM sections ORDER BY projectId ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<Section>

    @Query("DELETE FROM sections")
    suspend fun deleteAll()

    @Query("DELETE FROM sections WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    @Query("UPDATE sections SET blockId = :blockId WHERE id IN (:ids)")
    suspend fun setBlock(ids: List<Long>, blockId: Long?)

    @Query("UPDATE sections SET blockId = NULL WHERE blockId = :blockId")
    suspend fun detachFromBlock(blockId: Long)

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

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    fun observeByProject(projectId: Long): Flow<List<Block>>

    /** Вкладки всех проектов — для резервной копии. */
    @Query("SELECT * FROM blocks ORDER BY projectId ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<Block>

    @Query("DELETE FROM blocks")
    suspend fun deleteAll()

    @Query("DELETE FROM blocks WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    @Query("UPDATE blocks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Insert
    suspend fun insert(block: Block): Long

    @Insert
    suspend fun insertAll(blocks: List<Block>)

    @Update
    suspend fun update(block: Block)

    @Delete
    suspend fun delete(block: Block)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY id ASC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getAll(): List<Project>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(project: Project): Long

    @Insert
    suspend fun insertAll(projects: List<Project>)

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)
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

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        // Всё, что было, переходит в первый проект
        db.execSQL(
            "INSERT INTO `projects` (`id`, `name`, `createdAt`) " +
                "VALUES (1, 'Проект 1', ${System.currentTimeMillis()})"
        )
        db.execSQL("ALTER TABLE `sections` ADD COLUMN `projectId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `test_cases` ADD COLUMN `projectId` INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`projectId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        // Существующие разделы пока без вкладки
        db.execSQL("ALTER TABLE `sections` ADD COLUMN `blockId` INTEGER")
    }
}

/** При первом создании базы (чистая установка) сразу заводим первый проект. */
private val SeedFirstProject = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO `projects` (`id`, `name`, `createdAt`) " +
                "VALUES (1, 'Проект 1', ${System.currentTimeMillis()})"
        )
    }
}

@Database(
    entities = [TestCase::class, Section::class, Project::class, Block::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TestCaseDao
    abstract fun sections(): SectionDao
    abstract fun projects(): ProjectDao
    abstract fun blocks(): BlockDao

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
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    .addCallback(SeedFirstProject)
                    .build()
                    .also { instance = it }
            }
    }
}
