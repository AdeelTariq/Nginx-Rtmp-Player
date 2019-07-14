package com.winterparadox.nginxplayer.monitorwall

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.winterparadox.nginxplayer.common.Rtmp
import com.winterparadox.nginxplayer.common.Stream
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.util.*

class MonitorViewModel (api : NGINXServerApi) : ViewModel () {

    companion object {
        val INTERVAL = 2000L
    }

    private val _streams = MutableLiveData<List<Stream>> ()
    val streams: LiveData<List<Stream>>
        get() = _streams

    private var timer: Timer = Timer()

    init {
        _streams.value = emptyList()

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                api.stats().enqueue(object : Callback<Rtmp> {

                    override fun onFailure(call: Call<Rtmp>, t: Throwable) {
                        Timber.e("Failure: ${t.message}")
                    }

                    override fun onResponse(call: Call<Rtmp>, response: Response<Rtmp>) {
                        val newList = response.body()?.server?.application?.live?.streams ?: emptyList()
                        val sum = (_streams.value ?: emptyList()) + newList

                        val uncommon : List<Stream> = sum.groupBy { it.name }
                            .filter { it.value.size == 1 }
                            .flatMap { it.value }

                        if (uncommon.isEmpty()) {
                            return
                        }

                        if (newList.size > _streams.value?.size ?: 0) {
                            _streams.value = (_streams.value ?: emptyList()) + uncommon
                        } else {
                            _streams.value = (_streams.value ?: emptyList()) - uncommon
                        }
                        Timber.i("Success: ${_streams.value}")
                    }
                })
            }
        }, 0, INTERVAL)
    }

    override fun onCleared() {
        super.onCleared()
        timer.cancel()
        timer.cancel()

    }
}