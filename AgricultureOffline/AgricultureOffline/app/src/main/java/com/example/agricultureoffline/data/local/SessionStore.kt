package com.example.agricultureoffline.data.local

import android.content.Context
import android.content.SharedPreferences

data class UserSession(
    val isLoggedIn: Boolean,
    val farmerId: Int,
    val farmerName: String,
    val phoneNumber: String,
    val smsConsent: Boolean = true
)

object SessionStore {
    private const val PREFS_NAME = "agrivision_session_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_FARMER_ID = "farmer_id"
    private const val KEY_FARMER_NAME = "farmer_name"
    private const val KEY_PHONE_NUMBER = "phone_number"
    private const val KEY_SMS_CONSENT = "sms_consent"
    private const val KEY_WELCOME_SMS_QUEUED = "welcome_sms_queued"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSession(context: Context): UserSession {
        val sp = prefs(context)
        val isLoggedIn = sp.getBoolean(KEY_IS_LOGGED_IN, false)
        val farmerId = sp.getInt(KEY_FARMER_ID, 0)
        val farmerName = sp.getString(KEY_FARMER_NAME, "") ?: ""
        val phone = sp.getString(KEY_PHONE_NUMBER, "") ?: ""
        val consent = sp.getBoolean(KEY_SMS_CONSENT, true)
        return UserSession(
            isLoggedIn = isLoggedIn && farmerId > 0,
            farmerId = farmerId,
            farmerName = farmerName,
            phoneNumber = phone,
            smsConsent = consent
        )
    }

    fun saveSession(
        context: Context,
        farmerId: Int,
        farmerName: String,
        phoneNumber: String,
        smsConsent: Boolean = true
    ) {
        prefs(context).edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putInt(KEY_FARMER_ID, farmerId)
            .putString(KEY_FARMER_NAME, farmerName)
            .putString(KEY_PHONE_NUMBER, phoneNumber)
            .putBoolean(KEY_SMS_CONSENT, smsConsent)
            .apply()
    }

    fun clearSession(context: Context) {
        val sp = prefs(context)
        sp.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putInt(KEY_FARMER_ID, 0)
            .putString(KEY_FARMER_NAME, "")
            .putString(KEY_PHONE_NUMBER, "")
            .apply()
    }

    fun isWelcomeSmsQueued(context: Context, phone: String): Boolean {
        val cleanPhone = phone.replace("+", "").trim()
        return prefs(context).getBoolean("${KEY_WELCOME_SMS_QUEUED}_$cleanPhone", false)
    }

    fun setWelcomeSmsQueued(context: Context, phone: String, queued: Boolean = true) {
        val cleanPhone = phone.replace("+", "").trim()
        prefs(context).edit()
            .putBoolean("${KEY_WELCOME_SMS_QUEUED}_$cleanPhone", queued)
            .apply()
    }
}
