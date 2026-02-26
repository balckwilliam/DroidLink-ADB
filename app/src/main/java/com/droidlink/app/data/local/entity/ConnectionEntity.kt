package com.droidlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing device connection history.
 */
@Entity(
    tableName = "connections",
    indices = [Index(value = ["host", "port"], unique = true)]
)
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val host: String,
    val port: Int,
    val name: String = "",
    val lastConnected: Long = System.currentTimeMillis()
)
