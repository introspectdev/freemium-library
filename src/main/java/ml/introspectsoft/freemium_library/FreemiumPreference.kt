/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.content.Context

class FreemiumPreference(private var context: Context?) {
    companion object {
        const val INVALID_TOKEN = "INVALID"
        const val PREFERENCE_KEY = "freemium_purchase_token"
        const val PREFERENCE_FILE = "freemium_purchase_preferences"
    }

    val token get() = _token
    val isPremium get() = _token != INVALID_TOKEN

    private var _token: String = INVALID_TOKEN

    init {
        load()
    }

    fun load(): Boolean {
        val prefs = context?.getSharedPreferences(
                PREFERENCE_FILE, Context.MODE_PRIVATE
        )
        _token = prefs?.getString(PREFERENCE_KEY, INVALID_TOKEN) ?: INVALID_TOKEN

        return isPremium
    }

    fun save(purchaseToken: String) {
        _token = purchaseToken
        val prefs = context?.getSharedPreferences(
                PREFERENCE_FILE, Context.MODE_PRIVATE
        )
        prefs?.edit()?.putString(PREFERENCE_KEY, purchaseToken)?.apply()
    }

    fun destroy() {
        context = null
    }
}