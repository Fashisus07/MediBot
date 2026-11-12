package com.medibot.g1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionHistoryDao {
    @Query("SELECT * FROM mission_history ORDER BY endTime DESC")
    fun getAllMissions(): Flow<List<MissionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: MissionHistoryEntity)

    @Query("SELECT COUNT(*) FROM mission_history")
    suspend fun getMissionCount(): Int

    @Query("DELETE FROM mission_history")
    suspend fun clearAll()
}
