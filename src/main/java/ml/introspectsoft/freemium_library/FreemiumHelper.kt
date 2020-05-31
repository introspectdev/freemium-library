/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.content.Context
import com.android.billingclient.api.*
import timber.log.Timber

class FreemiumHelper(context: Context, private val premiumSku: String) :
    PurchasesUpdatedListener,
    SkuDetailsResponseListener {
    private var billingClient: BillingClient =
        BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
    private var premiumSkuDetails: SkuDetails? = null

    val isReady get() = billingClient.isReady

    // Default to true
    var isAvailable = true

    // From our check if it's a premium version or not. Just saying no for now until we get the rest working.
    val isPremium get() = true

    init {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

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
/*
    fun purchasePro(activity: Activity) {
        //connect()
        // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
        val flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(premiumSkuDetails)
            .build()
        //val responseCode = billingClient.launchBillingFlow(activity, flowParams)
    }
*/
    private fun setStates(billingResult: BillingResult?) {
        when (billingResult?.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.v("Billing connected")
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                Timber.v("Billing unavailable on this device.")
                // Play services are unavailable on the device
                isAvailable = false
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                Timber.w("Billing service unavailable")
            }
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> {
                Timber.w("Billing service timeout")
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                Timber.v("Billing service disconnected")
            }
            else -> {
                Timber.e(
                    "Billing connection failed. Unknown response: %s",
                    (billingResult?.responseCode).toString()
                )
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult?, data: MutableList<Purchase>?) {
        Timber.v("onPurchasesUpdated:$result:$data")
    }

    override fun onSkuDetailsResponse(result: BillingResult?, data: MutableList<SkuDetails>?) {
        if (result?.responseCode == BillingClient.BillingResponseCode.OK && data != null) {
            for (skuDetails in data) {
                if (premiumSku == skuDetails.sku) {
                    premiumSkuDetails = skuDetails
                }
            }
            Timber.d("SKU data result: $data")
        } else {
            Timber.d("onSkuDetailsResponse fail. responseCode: ${result?.responseCode}")
        }

        Timber.d("Sku result: $premiumSkuDetails")
    }
}