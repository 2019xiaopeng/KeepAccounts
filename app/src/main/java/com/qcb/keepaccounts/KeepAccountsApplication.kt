package com.qcb.keepaccounts

import android.app.Application
import com.qcb.keepaccounts.data.AppContainer
import com.qcb.keepaccounts.data.DefaultAppContainer

class KeepAccountsApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
