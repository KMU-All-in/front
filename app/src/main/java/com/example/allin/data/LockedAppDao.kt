package com.example.allin.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps")
    fun getAllLockedApps(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps")
    suspend fun getLockedAppsList(): List<LockedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: LockedApp)

    @Delete
    suspend fun delete(app: LockedApp)

    @Query("DELETE FROM locked_apps")
    suspend fun deleteAll()
}
