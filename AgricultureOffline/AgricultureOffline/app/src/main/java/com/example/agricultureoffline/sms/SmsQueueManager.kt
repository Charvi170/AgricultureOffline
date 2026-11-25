package com.example.agricultureoffline.sms

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.example.agricultureoffline.data.local.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class QueuedSmsMessage(
    val requestId: String,
    val countryCode: String,
    val nationalNumber: String,
    val timestamp: Long,
    val status: String, // PENDING, ACCEPTED, FAILED, DELIVERED
    val retryCount: Int = 0,
    val lastAttempt: Long = 0L,
    val lastError: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("requestId", requestId)
        put("countryCode", countryCode)
        put("nationalNumber", nationalNumber)
        put("timestamp", timestamp)
        put("status", status)
        put("retryCount", retryCount)
        put("lastAttempt", lastAttempt)
        put("lastError", lastError)
    }

    companion object {
        fun fromJson(json: JSONObject): QueuedSmsMessage = QueuedSmsMessage(
            requestId = json.optString("requestId", UUID.randomUUID().toString()),
            countryCode = json.optString("countryCode", "+91"),
            nationalNumber = json.optString("nationalNumber", ""),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            status = json.optString("status", "PENDING"),
            retryCount = json.optInt("retryCount", 0),
            lastAttempt = json.optLong("lastAttempt", 0L),
            lastError = json.optString("lastError", "")
        )
    }
}

object SmsQueueManager {

    private const val TAG = "SmsQueueManager"
    private const val PREFS_NAME = "agrivision_sms_queue"
    private const val KEY_MESSAGES = "queued_sms_messages"
    private const val MAX_RETRIES = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private val remoteDataSource = OtpRemoteDataSource()

    fun init(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            registerNetworkMonitor(appContext)
            triggerSync(appContext)
        }
    }

    private fun registerNetworkMonitor(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available, triggering SMS queue sync")
                    triggerSync(context)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                info != null && info.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    fun enqueueWelcomeSms(context: Context, countryCode: String, nationalNumber: String) {
        val cleanPhone = nationalNumber.trim()
        if (cleanPhone.isBlank()) return

        // Consent check & duplicate prevention across app restarts and logins
        val session = SessionStore.getSession(context)
        if (!session.smsConsent) {
            Log.d(TAG, "SMS consent is false, skipping welcome SMS")
            return
        }

        if (SessionStore.isWelcomeSmsQueued(context, cleanPhone)) {
            Log.d(TAG, "Welcome SMS already queued for phone: $cleanPhone. Skipping duplicate.")
            return
        }

        synchronized(this) {
            val messages = loadQueue(context).toMutableList()
            val alreadyQueued = messages.any { it.nationalNumber == cleanPhone }
            if (alreadyQueued) {
                SessionStore.setWelcomeSmsQueued(context, cleanPhone, true)
                return
            }

            val newMsg = QueuedSmsMessage(
                requestId = UUID.randomUUID().toString(),
                countryCode = countryCode,
                nationalNumber = cleanPhone,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            messages.add(newMsg)
            saveQueue(context, messages)
            SessionStore.setWelcomeSmsQueued(context, cleanPhone, true)
            Log.d(TAG, "Enqueued welcome SMS for $cleanPhone (requestId: ${newMsg.requestId})")
        }

        triggerSync(context.applicationContext)
    }

    fun triggerSync(context: Context) {
        scope.launch {
            syncInternal(context)
        }
    }

    private suspend fun syncInternal(context: Context) = withContext(Dispatchers.IO) {
        if (!isOnline(context)) {
            Log.d(TAG, "Device is offline. Queued SMS will be synced when internet resumes.")
            return@withContext
        }

        if (!isSyncing.compareAndSet(false, true)) {
            return@withContext
        }

        try {
            val messages = synchronized(this@SmsQueueManager) {
                loadQueue(context)
            }

            if (messages.isEmpty()) return@withContext

            val now = System.currentTimeMillis()
            val updatedList = mutableListOf<QueuedSmsMessage>()
            var modified = false

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            for (item in messages) {
                // If message is already accepted or delivered, keep it in queue
                if (item.status == "ACCEPTED" || item.status == "DELIVERED") {
                    updatedList.add(item)
                    continue
                }

                // If permanently failed after max retries, keep it
                if (item.status == "FAILED" && item.retryCount >= MAX_RETRIES) {
                    updatedList.add(item)
                    continue
                }

                // Check exponential backoff
                val backoffMs = when (item.retryCount) {
                    0 -> 0L
                    1 -> 5000L
                    2 -> 15000L
                    3 -> 30000L
                    4 -> 60000L
                    else -> 120000L
                }

                if (item.lastAttempt > 0 && (now - item.lastAttempt) < backoffMs) {
                    updatedList.add(item)
                    continue
                }

                // Attempt to send
                val dateStr = dateFormat.format(Date(item.timestamp))
                val timeStr = timeFormat.format(Date(item.timestamp))

                Log.d(TAG, "Syncing queued SMS for ${item.nationalNumber} (attempt ${item.retryCount + 1})...")
                val result = remoteDataSource.sendLoginSuccessNotification(
                    countryCode = item.countryCode,
                    nationalNumber = item.nationalNumber,
                    date = dateStr,
                    time = timeStr,
                    requestId = item.requestId
                )

                modified = true
                if (result.ok || result.code == "duplicate_prevented") {
                    val finalStatus = if (result.code == "delivered") "DELIVERED" else "ACCEPTED"
                    Log.d(TAG, "SMS for ${item.nationalNumber} successfully synchronized: $finalStatus")
                    updatedList.add(
                        item.copy(
                            status = finalStatus,
                            lastAttempt = System.currentTimeMillis(),
                            lastError = ""
                        )
                    )
                } else {
                    val isConfigIssue = result.code == "not_configured" || result.code == "provider_unconfigured"
                    val newRetryCount = if (isConfigIssue) item.retryCount else item.retryCount + 1
                    val newStatus = if (!isConfigIssue && newRetryCount >= MAX_RETRIES) "FAILED" else "PENDING"
                    Log.w(TAG, "SMS sync failed for ${item.nationalNumber}: ${result.message} (status: $newStatus)")
                    updatedList.add(
                        item.copy(
                            status = newStatus,
                            retryCount = newRetryCount,
                            lastAttempt = System.currentTimeMillis(),
                            lastError = result.message
                        )
                    )
                }
            }

            if (modified) {
                synchronized(this@SmsQueueManager) {
                    saveQueue(context, updatedList)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing SMS queue: ${e.message}", e)
        } finally {
            isSyncing.set(false)
        }
    }

    @Synchronized
    private fun loadQueue(context: Context): List<QueuedSmsMessage> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_MESSAGES, null) ?: return emptyList()
        val list = mutableListOf<QueuedSmsMessage>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                list.add(QueuedSmsMessage.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing queued SMS: ${e.message}")
        }
        return list
    }

    @Synchronized
    private fun saveQueue(context: Context, messages: List<QueuedSmsMessage>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        messages.forEach { array.put(it.toJson()) }
        sp.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun getPendingCount(context: Context): Int {
        return loadQueue(context).count { it.status == "PENDING" }
    }
}
