package com.kori.korichat.model.aiagent

data class ReqCreateSession(
    val state: SessionState
)

data class SessionState(
    val auth_token: String?,
    val locale: String?,
    val device_id: String?,
    val customer_type: String?
)

data class ReqRunAgent(
    val app_name: String,
    val user_id: String,
    val session_id: String,
    val new_message: AgentMessagePart,
    val streaming: Boolean = false
)

data class AgentMessagePart(
    val role: String,
    val parts: List<AgentTextPart>
)

data class AgentTextPart(
    val text: String
)

data class ResAgentSession(
    val id: String?
)

data class ResRunEvent(
    val content: AgentMessagePart?,
    val author: String?,
    val partial: Boolean?
)
