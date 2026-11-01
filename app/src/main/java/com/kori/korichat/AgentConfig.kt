package com.kori.korichat

import java.util.Locale

/**
 * The four session fields the chat sends to the ADK agent, pulled out of the
 * source app's GlobalStorage god-object. In the bank app these come from login;
 * here they're standalone defaults. [authToken] is sent to the agent's
 * get_homepage_data tool — with no real token the agent just reports it has none.
 */
object AgentConfig {
    var authToken: String? = null
    var locale: String = Locale.getDefault().language
    var deviceId: String = "kori-chat-standalone"
    var customerType: String = "personal"
}
