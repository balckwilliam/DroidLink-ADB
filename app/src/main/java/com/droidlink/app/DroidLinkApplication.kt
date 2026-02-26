package com.droidlink.app

import android.app.Application
import com.droidlink.app.adb.connection.AdbConnection
import com.droidlink.app.adb.crypto.AdbKeyManager
import com.droidlink.app.data.local.AppDatabase
import com.droidlink.app.data.repository.AdbRepositoryImpl
import com.droidlink.app.data.repository.ConnectionRepositoryImpl
import com.droidlink.app.domain.repository.AdbRepository
import com.droidlink.app.domain.repository.ConnectionRepository

class DroidLinkApplication : Application() {

    lateinit var adbConnection: AdbConnection
        private set

    lateinit var connectionRepository: ConnectionRepository
        private set

    lateinit var adbRepository: AdbRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val keyManager = AdbKeyManager(this)
        adbConnection = AdbConnection(keyManager)

        val database = AppDatabase.getInstance(this)
        connectionRepository = ConnectionRepositoryImpl(database.connectionDao())
        adbRepository = AdbRepositoryImpl(adbConnection)
    }
}
