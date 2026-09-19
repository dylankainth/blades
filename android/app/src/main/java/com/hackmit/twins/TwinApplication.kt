package com.hackmit.twins

import android.app.Application
import com.facebook.FacebookSdk

class TwinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Explicit init (rather than relying purely on manifest meta-data)
        // so Facebook Login works even if SDK auto-init is ever disabled.
        FacebookSdk.sdkInitialize(applicationContext)
    }
}
