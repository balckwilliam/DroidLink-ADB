package com.droidlink.app.domain.repository

import com.droidlink.app.domain.model.DeviceConnection
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing device connections.
 */
interface ConnectionRepository {
    fun getAllConnections(): Flow<List<DeviceConnection>>
    suspend fun getConnection(id: Long): DeviceConnection?
    suspend fun addConnection(connection: DeviceConnection): Long
    suspend fun updateConnection(connection: DeviceConnection)
    suspend fun deleteConnection(connection: DeviceConnection)
    suspend fun findByAddress(host: String, port: Int): DeviceConnection?
}
