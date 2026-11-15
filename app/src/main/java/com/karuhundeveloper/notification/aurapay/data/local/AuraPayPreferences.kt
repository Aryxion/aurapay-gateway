package com.karuhundeveloper.notification.aurapay.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.karuhundeveloper.notification.aurapay.model.AuraPayConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuraPayPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aurapay_config_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow = _configFlow.asStateFlow()

    fun loadConfig(): AuraPayConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return AuraPayConfig()
        return try {
            gson.fromJson(json, AuraPayConfig::class.java) ?: AuraPayConfig()
        } catch (_: Exception) {
            AuraPayConfig()
        }
    }

    fun saveConfig(config: AuraPayConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_CONFIG, json).apply()
        _configFlow.value = config
    }

    companion object {
        private const val KEY_CONFIG = "saved_aurapay_config"

        @Volatile
        private var INSTANCE: AuraPayPreferences? = null

        fun getInstance(context: Context): AuraPayPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuraPayPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
