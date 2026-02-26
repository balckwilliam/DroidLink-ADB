package com.droidlink.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.droidlink.app.data.local.entity.ConnectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for connection history.
 */
@Dao
interface ConnectionDao {

    @Query("SELECT * FROM connections ORDER BY lastConnected DESC")
    fun getAllConnections(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getConnectionById(id: Long): ConnectionEntity?

    @Query("SELECT * FROM connections WHERE host = :host AND port = :port")
    suspend fun findByAddress(host: String, port: Int): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: ConnectionEntity): Long

    @Update
    suspend fun update(connection: ConnectionEntity)

    @Delete
    suspend fun delete(connection: ConnectionEntity)
}
