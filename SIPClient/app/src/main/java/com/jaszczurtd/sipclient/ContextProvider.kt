package com.jaszczurtd.sipclient

import android.content.Context
import androidx.multidex.MultiDexApplication

class ContextProvider : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: ContextProvider? = null

        @JvmStatic
        val context: Context
            get() = instance!!.applicationContext
    }
}
