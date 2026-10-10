package com.membora.app

import android.app.Application
import com.membora.app.core.media.ImageKitManager
import com.membora.app.data.MemboraRepository
import com.membora.app.firebase.FirebaseServices
import com.membora.app.firebase.MemboraNotifications
import com.membora.app.automation.AutomationScheduler
import com.google.firebase.auth.FirebaseAuth

class MemboraApplication : Application() {
    lateinit var repository: MemboraRepository
        private set

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        auth.currentUser?.uid?.let(repository::connectFirebase) ?: repository.disconnectFirebase()
    }

    override fun onCreate() {
        super.onCreate()
        ImageKitManager.initialize(this)
        MemboraNotifications.createChannel(this)
        FirebaseServices.initialize(this)
        repository = MemboraRepository()
        FirebaseServices.auth?.let { auth ->
            auth.addAuthStateListener(authListener)
            auth.currentUser?.uid?.let(repository::connectFirebase)
        }
        AutomationScheduler.schedule(this)
    }
}
