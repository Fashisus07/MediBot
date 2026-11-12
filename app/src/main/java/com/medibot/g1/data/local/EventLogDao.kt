package com.medibot.g1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_log WHERE category = :category ORDER BY timestamp DESC")
    fun getEventsByCategory(category: String): Flow<List<EventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventLogEntity)

    @Query("SELECT COUNT(*) FROM event_log")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM event_log")
    suspend fun clearAll()
}
