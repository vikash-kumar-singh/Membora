package com.membora.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.membora.app.MemboraApplication

/** Owns the workspace and keeps it connected to the currently authenticated Firebase user. */
class MemboraViewModel(application: Application) : AndroidViewModel(application) {
    val repository: MemboraRepository = (application as MemboraApplication).repository
}
