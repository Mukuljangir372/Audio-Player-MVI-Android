package com.mukul.jan.audioplayer

import androidx.lifecycle.ViewModel
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ViewModelFactory {
    companion object {
        @Volatile
        private var instance: ViewModelFactory? = null

        fun create(): ViewModelFactory {
            if (instance != null) return instance as ViewModelFactory
            synchronized(this) {
                if (instance == null) {
                    instance = ViewModelFactory()
                }
            }
            return instance!!
        }
    }

    private val viewModels = ConcurrentHashMap<String, ViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> get(default: T): T {
        val key = default::class.java.name
        val vm = viewModels[key]
        return if (vm != null) {
            vm as T
        } else {
            viewModels[key] = default
            default
        }
    }
}