/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class FreemiumHelper(private val context: Context, private val premiumSku: String) :
    PurchasesUpdatedListener,
    SkuDetailsResponseListener {
    private var billingClient: BillingClient =
        BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
    private var premiumSkuDetails: SkuDetails? = null

    val isReady get() = billingClient.isReady

    // Default to true
    var isAvailable = true

    // From our check if it's a premium version or not. Just saying no for now until we get the rest working.
    val isPremium get() = false

    private fun connect(callback: (BillingResult?) -> Unit) {
        if (!billingClient.isReady) {
            billingClient.startConnection(
                BillingClientRequest(
                    callback
                )
            )
        } else {
            callback(null)
        }
    }

    fun querySkuDetails() = connect {
        setStates(it)
        val skuList = listOf(premiumSku)

        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build(), this)
    }

    fun purchasePro(activity: Activity) {
        //connect()
        // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
        val flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(premiumSkuDetails)
            .build()
        val responseCode = billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun setStates(billingResult: BillingResult?) {
        when (billingResult?.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Log.d("BILLING", "Connected")
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                Log.d("BILLING", "BILLING_UNAVAILABLE")
                // Play services are unavailable on the device
                isAvailable = false
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Log.d("BILLING", "SERVICE_UNAVAILABLE")
            }
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                Log.d("BILLING", "SERVICE_TIMEOUT")
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                Log.d("BILLING", "SERVICE_DISCONNECTED")
            }
            else -> {
                Log.d("BILLING", "Connection failed: " + (billingResult?.responseCode).toString())
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult?, data: MutableList<Purchase>?) {
        Log.d("BILLING", "onPurchaseUpdated:$result:$data")
    }

    override fun onSkuDetailsResponse(result: BillingResult?, data: MutableList<SkuDetails>?) {
        if (result?.responseCode == BillingClient.BillingResponseCode.OK && data != null) {
            for (skuDetails in data) {
                if (premiumSku == skuDetails.sku) {
                    premiumSkuDetails = skuDetails
                }
            }
            Log.d("SKU", "Data: $data")
        } else {
            Log.d("SKU", "onSkuDetailsResponse response: ${result?.responseCode}")
        }

        Log.d("mysku", premiumSkuDetails.toString())
    }
}