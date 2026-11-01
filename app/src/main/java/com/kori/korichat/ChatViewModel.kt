package com.kori.korichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kori.korichat.model.aiagent.AgentMessagePart
import com.kori.korichat.model.aiagent.AgentTextPart
import com.kori.korichat.model.aiagent.ReqCreateSession
import com.kori.korichat.model.aiagent.ReqRunAgent
import com.kori.korichat.model.aiagent.SessionState
import com.kori.korichat.network.AiAgentApiServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val APP_NAME = "local_adk_agent"
private const val USER_ID = "user"

class ChatViewModel : ViewModel() {
    private val aiAgentApiServices = AiAgentApiServices.getInstance()
    private var sessionId: String? = null

    fun startSession(onReady: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val session = aiAgentApiServices.createSession(
                    APP_NAME,
                    USER_ID,
                    ReqCreateSession(
                        state = SessionState(
                            auth_token = AgentConfig.authToken,
                            locale = AgentConfig.locale,
                            device_id = AgentConfig.deviceId,
                            customer_type = AgentConfig.customerType
                        )
                    )
                )
                val id = session.id
                if (id.isNullOrEmpty()) {
                    onError("Agent session did not return an id")
                } else {
                    sessionId = id
                    onReady("Agent is ready")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Something went wrong")
            }
        }
    }

    fun sendMessage(text: String, onReply: (String) -> Unit, onError: (String) -> Unit) {
        val currentSessionId = sessionId ?: run {
            onError("No active session")
            return
        }
        viewModelScope.launch {
            try {
                val events = aiAgentApiServices.run(
                    ReqRunAgent(
                        app_name = APP_NAME,
                        user_id = USER_ID,
                        session_id = currentSessionId,
                        new_message = AgentMessagePart(role = "user", parts = listOf(AgentTextPart(text)))
                    )
                )
                val reply = events
                    .filter { it.content?.role == "model" }
                    .flatMap { it.content?.parts.orEmpty() }
                    .mapNotNull { it.text }
                    .joinToString(separator = "\n")
                if (reply.isEmpty()) {
                    onError("Agent gave no reply")
                } else {
                    onReply(reply)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Something went wrong")
            }
        }
    }

    /**
     * Streams the agent's reply via `/run_sse`, calling [onChunk] with the
     * cumulative text so far as each token/partial event arrives.
     */
    fun sendMessageStreaming(
        text: String,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentSessionId = sessionId ?: run {
            onError("No active session")
            return
        }
        val accumulated = StringBuilder()
        var gotAnyText = false
        AiAgentApiServices.streamRun(
            reqRunAgent = ReqRunAgent(
                app_name = APP_NAME,
                user_id = USER_ID,
                session_id = currentSessionId,
                new_message = AgentMessagePart(role = "user", parts = listOf(AgentTextPart(text))),
                streaming = true
            ),
            onEvent = { event ->
                if (event.content?.role == "model") {
                    val chunkText = event.content.parts.mapNotNull { it.text }.joinToString("")
                    if (chunkText.isNotEmpty()) {
                        gotAnyText = true
                        if (event.partial == true) {
                            accumulated.append(chunkText)
                        } else {
                            accumulated.setLength(0)
                            accumulated.append(chunkText)
                        }
                        val snapshot = accumulated.toString()
                        viewModelScope.launch(Dispatchers.Main.immediate) { onChunk(snapshot) }
                    }
                }
            },
            onDone = {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (!gotAnyText) onError("Agent gave no reply") else onDone()
                }
            },
            onError = { t ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    onError(t.message ?: "Something went wrong")
                }
            }
        )
    }
}
