/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import com.android.billingclient.api.*

class BillingClientRequest(callback: (BillingResult?) -> Unit) : BillingClientStateListener {
    var onConnect : ((BillingResult?) -> Unit)? = null
    var result : BillingResult? = null

    init {
        onConnect = callback
    }

    override fun onBillingServiceDisconnected() {
        // "Not yet implemented"
    }

    override fun onBillingSetupFinished(response: BillingResult?) {
        result = response

        if (response?.responseCode == BillingClient.BillingResponseCode.OK) {
            onConnect?.let { it(response) }
        }
    }
}