package com.winterparadox.nginxplayer.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.winterparadox.nginxplayer.monitorwall.MonitorViewModel
import com.winterparadox.nginxplayer.monitorwall.NGINXServerApi

class MonitorViewModelFactory(private val api: NGINXServerApi) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MonitorViewModel::class.java)) {
            return MonitorViewModel (api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}