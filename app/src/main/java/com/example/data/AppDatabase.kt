package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceAliasDao {
    @Query("SELECT * FROM device_aliases")
    fun getAllAliasesFlow(): Flow<List<DeviceAlias>>

    @Query("SELECT * FROM device_aliases WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getAliasForDevice(macAddress: String): DeviceAlias?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: DeviceAlias)

    @Query("DELETE FROM device_aliases WHERE macAddress = :macAddress")
    suspend fun deleteAlias(macAddress: String)
}

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 200")
    fun getRecentHistoryFlow(): Flow<List<ScanHistory>>

    @Query("SELECT * FROM scan_history WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 50")
    fun getDeviceHistoryFlow(macAddress: String): Flow<List<ScanHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScanHistory)

    @Query("DELETE FROM scan_history")
    suspend fun clearAllHistory()
}

@Database(entities = [DeviceAlias::class, ScanHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceAliasDao(): DeviceAliasDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "airpulse_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
