package com.droidlink.app.data.repository

import com.droidlink.app.data.local.dao.ConnectionDao
import com.droidlink.app.data.local.entity.ConnectionEntity
import com.droidlink.app.domain.model.DeviceConnection
import com.droidlink.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of ConnectionRepository using Room database.
 */
class ConnectionRepositoryImpl(
    private val connectionDao: ConnectionDao
) : ConnectionRepository {

    override fun getAllConnections(): Flow<List<DeviceConnection>> {
        return connectionDao.getAllConnections().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getConnection(id: Long): DeviceConnection? {
        return connectionDao.getConnectionById(id)?.toDomainModel()
    }

    override suspend fun addConnection(connection: DeviceConnection): Long {
        return connectionDao.insert(connection.toEntity())
    }

    override suspend fun updateConnection(connection: DeviceConnection) {
        connectionDao.update(connection.toEntity())
    }

    override suspend fun deleteConnection(connection: DeviceConnection) {
        connectionDao.delete(connection.toEntity())
    }

    override suspend fun findByAddress(host: String, port: Int): DeviceConnection? {
        return connectionDao.findByAddress(host, port)?.toDomainModel()
    }

    private fun ConnectionEntity.toDomainModel(): DeviceConnection {
        return DeviceConnection(
            id = id,
            host = host,
            port = port,
            name = name,
            lastConnected = lastConnected
        )
    }

    private fun DeviceConnection.toEntity(): ConnectionEntity {
        return ConnectionEntity(
            id = id,
            host = host,
            port = port,
            name = name,
            lastConnected = lastConnected
        )
    }
}
