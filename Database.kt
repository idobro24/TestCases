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
import kotlinx.coroutines.flow.Flow

@Dao
interface TestCaseDao {
    @Query("SELECT * FROM test_cases ORDER BY id DESC")
    fun observeAll(): Flow<List<TestCase>>

    @Query("SELECT * FROM test_cases WHERE id = :id")
    suspend fun getById(id: Long): TestCase?

    @Insert
    suspend fun insert(testCase: TestCase): Long

    @Update
    suspend fun update(testCase: TestCase)

    @Delete
    suspend fun delete(testCase: TestCase)
}

@Database(entities = [TestCase::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TestCaseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "testcases.db"
                ).build().also { instance = it }
            }
    }
}
