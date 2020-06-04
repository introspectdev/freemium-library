/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.receiveOrNull
import ml.introspectsoft.cobilling.CoBilling
import timber.log.Timber

class Freemium(
        activity: Activity, private val premiumSku: String, useScope: CoroutineScope? = null
) {

    private var premiumItem: SkuDetails? = null
    private var testItem: SkuDetails? = null
    private val testSku = "test_item"

    private var billing = CoBilling(activity)
    private var prefs = FreemiumPreference(activity.applicationContext)

    private var _isAvailable = false

    private val scope = useScope ?: MainScope()

    /**
     * Is the purchase button available
     */
    val isAvailable get() = _isAvailable

    /**
     * Is the premium version purchased
     */
    val isPremium get() = prefs.isPremium

    /**
     * Notification of change to isPremium. Only triggered when it becomes true.
     */
    val premiumChanged: BroadcastChannel<Boolean> = ConflatedBroadcastChannel()

    /**
     * onNewPurchase(purchase)
     */
    val newPurchase: BroadcastChannel<Purchase> = BroadcastChannel(CoBilling.BUFFER_SIZE)

    /**
     * onPendingPurchase(purchase)
     */
    val pendingPurchase: BroadcastChannel<Purchase> = BroadcastChannel(CoBilling.BUFFER_SIZE)

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                val receiver = billing.purchasesUpdated.openSubscription()
                launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
                    while (!receiver.isClosedForReceive) {
                        val purchases = receiver.receiveOrNull()
                        if (purchases == null) {
                            cancel()
                        }
                        purchases?.let { onPurchasesUpdated(it) }
                    }
                }

                // check if we're paid for
                if (!isPremium) {
                    checkPurchased()
                }

                // Trigger processing of pending new purchases
                billing.checkPurchases(BillingClient.SkuType.INAPP)
            }
        }
    }

    private suspend fun onPurchasesUpdated(update: Purchase.PurchasesResult) {
        if (update.billingResult.responseCode != BillingResponseCode.OK) {
            Timber.d(
                    "onPurchasesUpdated: Something probably went wrong. ResponseCode: %d, Purchases count: %d",
                    update.billingResult.responseCode,
                    update.purchasesList?.size
            )
            return
        }

        update.purchasesList?.forEach { purchase ->
            if (!purchase.isAcknowledged) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // handle premium sku ourselves
                    when (purchase.sku) {
                        premiumSku -> {
                            val result = billing.acknowledgePurchase(purchase)
                            if (result.responseCode == BillingResponseCode.OK) {
                                makePremium(purchase.purchaseToken)
                            } else {
                                Timber.d("acknowledge fail: %d", result.responseCode)
                            }
                        }
                        testSku    -> {
                            val result = billing.consumePurchase(purchase)
                            if (result.billingResult.responseCode == BillingResponseCode.OK) {
                                Timber.d("Consumed: %s", result.purchaseToken)
                            } else {
                                Timber.d("Consume failed: %d", result.billingResult.responseCode)
                            }
                        }
                        else       -> {
                            // Process any other new purchase in the app
                            newPurchase.send(purchase)
                        }
                    }
                }
                if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    // Informational pending purchase notification
                    pendingPurchase.send(purchase)
                }
            } else {
                // possibly redundant checks, but...
                if (purchase.sku == premiumSku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged) {

                    makePremium(purchase.purchaseToken)
                }
            }
        }
    }

    private suspend fun makePremium(tokenString: String) {
        prefs.save(tokenString)
        premiumChanged.offer(isPremium)
    }

    private suspend fun checkPurchased() {
        scope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            while (true) {
                val purchased = billing.getPurchasedInApps()
                if (purchased.billingResult.responseCode == BillingResponseCode.OK) {
                    purchased.purchasesList.forEach {
                        if (it.purchaseState == Purchase.PurchaseState.PURCHASED && it.sku == premiumSku) {
                            // Should eventually add some validation to verify the user really purchased it somehow
                            if (!isPremium) {
                                makePremium(it.purchaseToken)
                            }
                        }
                    }

                    // cancel because we got a response
                    cancel()
                }

                // Run again in 5 minutes
                delay(5 * 60 * 1000)
            }
        }
    }

    /**
     * Initiate the purchase of the premium version
     *
     * @param[observer] observer object to handle callbacks
     * @return false if information is still being loaded from the Play Store
     */
    suspend fun purchasePremium(): BillingResult {
        // first check, then query
        if (premiumItem == null) {
            val search = billing.queryInAppPurchases(premiumSku)
            search.skuDetailsList?.forEach {
                if (it.sku == premiumSku) {
                    premiumItem = it
                    _isAvailable = true
                }
            }
        }
        // second check
        if (premiumItem == null) {
            Timber.w("Cannot initiate purchase yet. no item found")
            return BillingResult.newBuilder().setResponseCode(-1).build()
        }

        premiumItem?.let {
            val result = billing.purchase(it)
            return result
        }

        Timber.wtf("purchasePremium() This code should not be reached.")

        // This should never be reached
        return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.DEVELOPER_ERROR)
                .build()
    }

    suspend fun purchaseTest(): BillingResult {
        // first check, then query
        if (testItem == null) {
            val search = billing.queryInAppPurchases(testSku)
            search.skuDetailsList?.forEach {
                if (it.sku == testSku) {
                    testItem = it
                    _isAvailable = true
                }
            }
        }
        // second check
        if (testItem == null) {
            Timber.w("Cannot initiate purchase yet. no item found")
            return BillingResult.newBuilder().setResponseCode(-1).build()
        }

        testItem?.let {
            val result = billing.purchase(it)
            return result
        }

        Timber.wtf("purchaseTest() This code should not be reached.")

        // This should never be reached
        return BillingResult.newBuilder()
                .setResponseCode(BillingResponseCode.DEVELOPER_ERROR)
                .build()
    }

    /**
     * Cleanup listeners and objects
     */
    fun close() {
        newPurchase.close()
        pendingPurchase.close()
        premiumChanged.close()

        prefs.destroy()
        billing.close()
    }
}