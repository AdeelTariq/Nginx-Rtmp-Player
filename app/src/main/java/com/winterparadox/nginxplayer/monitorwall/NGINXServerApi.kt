package com.winterparadox.nginxplayer.monitorwall

import com.winterparadox.nginxplayer.common.Rtmp
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET


fun build (url: String): NGINXServerApi {
    val retrofit = Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(SimpleXmlConverterFactory.createNonStrict(
                Persister(AnnotationStrategy())))
        .build()
    return retrofit.create(NGINXServerApi::class.java)
}

interface NGINXServerApi {

    @GET("stat")
    fun stats () : Call<Rtmp>

}