package com.mirrormood.security

import android.content.Context
import com.mirrormood.MirrorMoodApp
import java.security.MessageDigest
import java.util.UUID

object PinStorage {

    private const val KEY_HASH = "app_pin_hash"
    private const val KEY_SALT = "app_pin_salt"

    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_HASH, null).isNullOrEmpty()
    }

    fun savePin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        var salt = prefs.getString(KEY_SALT, null)
        if (salt.isNullOrEmpty()) {
            salt = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_SALT, salt).apply()
        }
        val hash = hashPin(pin, salt)
        prefs.edit().putString(KEY_HASH, hash).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)
        val hash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        return hashPin(pin, salt) == hash
    }

    fun clearPin(context: Context) {
        context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
