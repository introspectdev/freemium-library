/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.ReplaySubject
import ml.introspectsoft.rxbilling.BillingResponse
import ml.introspectsoft.rxbilling.Inventory
import ml.introspectsoft.rxbilling.PurchasesUpdate
import ml.introspectsoft.rxbilling.RxBilling
import timber.log.Timber

class Freemium(activity: Activity, private val premiumSku: String) {
    private var purchaseSubscription: Disposable? = null
    private var premiumInventory: Inventory? = null

    private var billing = RxBilling(activity)
    private var prefs = FreemiumPreference(activity.applicationContext)

    private var _isAvailable = false

    /**
     * Notification of change to isPremium. Only triggered when it becomes true.
     */
    val premiumChanged: @NonNull PublishSubject<Boolean> = PublishSubject.create()

    /**
     * Is the purchase button available
     */
    val isAvailable get() = _isAvailable

    /**
     * Is the premium version purchased
     */
    val isPremium get() = prefs.isPremium

    /**
     * onNewPurchase(purchase)
     */
    val newPurchase: @NonNull ReplaySubject<Purchase> = ReplaySubject.create<Purchase>()

    /**
     * onPendingPurchase(purchase)
     */
    val pendingPurchase: @NonNull ReplaySubject<Purchase> = ReplaySubject.create<Purchase>()

    init {
        // Start listening for purchase changes
        purchaseSubscription =
                billing.purchasesUpdated.subscribe { updates -> onPurchasesUpdated(updates) }

        billing.queryInAppPurchases(premiumSku).subscribe {
            if (it?.sku == premiumSku) {
                premiumInventory = it
                _isAvailable = true
            }

            checkPurchased()

            // Query for existing purchases for INAPP
            billing.checkPurchases(BillingClient.SkuType.INAPP)
        }

    }

    private fun onPurchasesUpdated(update: PurchasesUpdate) {
        if (update.result.responseCode != BillingResponse.OK) {
            Timber.d(
                    "Something probably went wrong. ResponseCode: %d, Purchases count: %d",
                    update.result.responseCode,
                    update.purchases?.count()
            )
            return
        }

        update.purchases?.forEach { purchase ->
            if (!purchase.isAcknowledged) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // handle premium sku ourselves
                    if (purchase.sku == premiumSku) {
                        billing.acknowledgePurchase(purchase).subscribe({
                                                                            if (it == BillingResponse.OK) {
                                                                                makePremium(purchase.purchaseToken)
                                                                            }
                                                                            // Should we do something here for other responses?
                                                                        }, {
                                                                            Timber.w(it)
                                                                        })
                    } else {
                        // Process any other new purchase in the app
                        newPurchase.onNext(purchase)
                    }
                }
                if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    // Informational pending purchase notification
                    pendingPurchase.onNext(purchase)
                }
            } else {
                // possibly redundant checks, but...
                if (purchase.sku == premiumSku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.isAcknowledged) {
                    makePremium(purchase.purchaseToken)
                }
            }
        }
    }

    private fun makePremium(tokenString: String) {
        prefs.save(tokenString)
        premiumChanged.onNext(isPremium)
    }

    private fun checkPurchased() {
        billing.purchasedInApps.subscribe({
                                              if (it.purchaseState == Purchase.PurchaseState.PURCHASED && it.productId == premiumSku) {
                                                  // Should eventually add some validation to verify the user really purchased it somehow
                                                  if (!isPremium) {
                                                      makePremium(it.purchaseToken)
                                                  }
                                              }
                                              // Ignore any other returned purchases

                                          }, {
                                              // Something went wrong
                                              Timber.w(it)
                                          })
    }

    /**
     * Initiate the purchase of the premium version
     *
     * @param[observer] observer object to handle callbacks
     * @return false if information is still being loaded from the Play Store
     */
    fun purchasePremium(observer: SingleObserver<BillingResult>) : Boolean {
        if (premiumInventory == null) {
            Timber.w("Cannot initiate purchase yet.")
            return false
        }

        premiumInventory?.let { it ->
            billing.purchase(it)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(observer)
        }
        return true
    }

    /**
     * Cleanup listeners and objects
     */
    fun destroy() {
        if (purchaseSubscription != null && purchaseSubscription?.isDisposed == false) {
            purchaseSubscription?.dispose()
        }
        prefs.destroy()
        billing.destroy()
    }
}