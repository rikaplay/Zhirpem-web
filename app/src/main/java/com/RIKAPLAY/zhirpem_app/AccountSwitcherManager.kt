package com.RIKAPLAY.zhirpem_app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LinkedAccount(
    val username: String,
    val name: String,
    val avatarUrl: String? = null
)

object AccountSwitcherManager {

    private const val PREFS_NAME = "linked_accounts_prefs"
    private const val KEY_ACCOUNTS = "accounts_json"
    const val MAX_ACCOUNTS = 10

    fun getLinkedAccounts(context: Context): List<LinkedAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val list = mutableListOf<LinkedAccount>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val username = obj.optString("username", "")
                val name = obj.optString("name", username)
                val avatarUrl = if (obj.has("avatarUrl") && !obj.isNull("avatarUrl")) obj.optString("avatarUrl") else null
                if (username.isNotEmpty()) {
                    list.add(LinkedAccount(username, name, avatarUrl))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addOrUpdateAccount(context: Context, account: LinkedAccount) {
        if (account.username.isEmpty() || account.username == "anonymous") return
        val currentList = getLinkedAccounts(context).toMutableList()
        val existingIndex = currentList.indexOfFirst { it.username == account.username }

        if (existingIndex != -1) {
            currentList[existingIndex] = account
        } else {
            if (currentList.size < MAX_ACCOUNTS) {
                currentList.add(account)
            }
        }
        saveList(context, currentList)
    }

    fun removeAccount(context: Context, username: String) {
        val currentList = getLinkedAccounts(context).toMutableList()
        currentList.removeAll { it.username == username }
        saveList(context, currentList)
    }

    private fun saveList(context: Context, list: List<LinkedAccount>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        list.take(MAX_ACCOUNTS).forEach { acc ->
            val obj = JSONObject()
            obj.put("username", acc.username)
            obj.put("name", acc.name)
            obj.put("avatarUrl", acc.avatarUrl ?: "")
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_ACCOUNTS, jsonArray.toString()).apply()
    }

    fun switchToAccount(context: Context, username: String): Boolean {
        val accounts = getLinkedAccounts(context)
        val target = accounts.find { it.username == username } ?: return false

        val sessionPrefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        sessionPrefs.edit()
            .putString("username", target.username)
            .putString("name", target.name)
            .putString("avatarUrl", target.avatarUrl ?: "")
            .putBoolean("is_logged_in", true)
            .apply()

        return true
    }
}
