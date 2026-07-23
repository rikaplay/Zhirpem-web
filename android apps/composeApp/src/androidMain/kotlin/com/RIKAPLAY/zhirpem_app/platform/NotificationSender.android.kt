package com.RIKAPLAY.zhirpem_app.platform

import android.text.Html
import com.RIKAPLAY.zhirpem_app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

actual class NotificationSender {
    private val client = OkHttpClient()

    actual suspend fun sendGlobalPush(title: String, body: String, imageUrl: String): Boolean {
        return try {
            val json = JSONObject()
            json.put("app_id", BuildConfig.ONESIGNAL_APP_ID)
            json.put("included_segments", JSONArray(listOf("Subscribed Users")))
            
            val contents = JSONObject()
            val plainText = Html.fromHtml(body, Html.FROM_HTML_MODE_COMPACT).toString()
            contents.put("en", plainText)
            json.put("contents", contents)
            
            val headings = JSONObject()
            headings.put("en", title)
            json.put("headings", headings)

            if (imageUrl.isNotEmpty()) {
                json.put("big_picture", imageUrl)
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://onesignal.com/api/v1/notifications")
                .post(requestBody)
                .addHeader("Authorization", "Basic ${BuildConfig.ONESIGNAL_REST_KEY}")
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }
}
