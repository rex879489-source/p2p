package com.example.p2pchat

import android.content.Context
import kotlin.random.Random

/**
 * Generates and stores a random username locally on-device.
 * No account, no server, no phone number. Just a fun label
 * used for display during a chat session.
 */
object Identity {
    private const val PREFS = "p2pchat_prefs"
    private const val KEY_NAME = "username"

    private val adjectives = listOf(
        "Brave", "Swift", "Silent", "Clever", "Mighty", "Calm", "Wild", "Lucky",
        "Bold", "Quick", "Gentle", "Fierce", "Sunny", "Cosmic", "Rapid", "Quiet"
    )
    private val nouns = listOf(
        "Otter", "Falcon", "Tiger", "Panda", "Wolf", "Eagle", "Fox", "Hawk",
        "Lynx", "Raven", "Dolphin", "Bear", "Heron", "Comet", "Ember", "Nova"
    )

    fun getOrCreateUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_NAME, null)?.let { return it }

        val name = "${adjectives.random()}${nouns.random()}${Random.nextInt(10, 99)}"
        prefs.edit().putString(KEY_NAME, name).apply()
        return name
    }
}
