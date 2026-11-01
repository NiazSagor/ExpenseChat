package com.kori.korichat.network

import com.google.gson.Gson
import com.kori.korichat.model.aiagent.ReqCreateSession
import com.kori.korichat.model.aiagent.ReqRunAgent
import com.kori.korichat.model.aiagent.ResAgentSession
import com.kori.korichat.model.aiagent.ResRunEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface AiAgentApiServices {
    @POST("apps/{appName}/users/{userId}/sessions")
    suspend fun createSession(
        @Path("appName") appName: String,
        @Path("userId") userId: String,
        @Body reqCreateSession: ReqCreateSession
    ): ResAgentSession

    @POST("run")
    suspend fun run(@Body reqRunAgent: ReqRunAgent): List<ResRunEvent>

    companion object {
        // ADK dev server (adk web . / adk api_server) reachable from the
        // Genymotion emulator at the host-loopback address 10.0.3.2.
        // Standard Android Studio AVDs use 10.0.2.2 instead.
        private const val BASE_URL = "http://10.0.3.2:8000/"
        private val gson = Gson()
        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
        var aiAgentApiServices: AiAgentApiServices? = null
        fun getInstance(): AiAgentApiServices {
            if (aiAgentApiServices == null) {
                aiAgentApiServices = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(okHttpClient)
                    .build()
                    .create(AiAgentApiServices::class.java)
            }
            return aiAgentApiServices!!
        }

        /**
         * Streams agent output via ADK's `/run_sse` endpoint (one JSON event per
         * SSE `data:` line). Returns the [EventSource] so the caller can [EventSource.cancel] it.
         */
        fun streamRun(
            reqRunAgent: ReqRunAgent,
            onEvent: (ResRunEvent) -> Unit,
            onDone: () -> Unit,
            onError: (Throwable) -> Unit
        ): EventSource {
            val request = Request.Builder()
                .url(BASE_URL + "run_sse")
                .post(gson.toJson(reqRunAgent).toRequestBody("application/json".toMediaType()))
                .build()
            return EventSources.createFactory(okHttpClient).newEventSource(
                request,
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String
                    ) {
                        onEvent(gson.fromJson(data, ResRunEvent::class.java))
                    }

                    override fun onClosed(eventSource: EventSource) = onDone()

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: okhttp3.Response?
                    ) {
                        onError(t ?: IllegalStateException("SSE failed: ${response?.code}"))
                    }
                }
            )
        }
    }
}
