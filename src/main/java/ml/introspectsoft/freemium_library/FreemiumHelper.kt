/*
 * Copyright (c) 2020. Jason Burgess
 */

package ml.introspectsoft.freemium_library

import android.app.Activity
import android.content.Context.MODE_PRIVATE
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.disposables.Disposable
import ml.introspectsoft.rxbilling.BillingResponse
import ml.introspectsoft.rxbilling.Inventory
import ml.introspectsoft.rxbilling.PurchasesUpdate
import ml.introspectsoft.rxbilling.RxBilling
import rx.subjects.PublishSubject
import rx.subjects.ReplaySubject
import timber.log.Timber
import java.text.SimpleDateFormat

class FreemiumHelper(private val activity: Activity, private val premiumSku: String) {
    companion object {
        const val INVALID_TOKEN = "INVALID"
        const val PREFERENCE_KEY = "freemium_purchase_token"
        const val PREFERENCE_FILE = "freemium_purchase_preferences"
    }

    private var preferenceToken: String = "invalid"

    private var purchaseSubscription: Disposable? = null
    private var billing = RxBilling(activity)
    private var premiumInventory: Inventory? = null
    private var _isPremium = false

    /**
     * Notification of change to isPremium. Only triggered when it becomes true.
     */
    val premiumChanged: @NonNull PublishSubject<Boolean> = PublishSubject.create<Boolean>()

    // TODO: Switch this to a get() only
    var isAvailable = false
    val isPremium get() = _isPremium

    /**
     * onNewPurchase(purchase)
     * TODO: See if ReplaySubject is going to work
     */
    val newPurchase: @NonNull ReplaySubject<Purchase> = ReplaySubject.create<Purchase>()

    /**
     * onPendingPurchase(purchase)
     * TODO: See if ReplaySubject is going to work
     */
    val pendingPurchase: @NonNull ReplaySubject<Purchase> = ReplaySubject.create<Purchase>()

    init {
        // get cached values
        loadPreference()

        // Start listening for purchase changes
        purchaseSubscription =
                billing.purchasesUpdated.subscribe { updates -> onPurchasesUpdated(updates) }

        billing.queryInAppPurchases(premiumSku).subscribe {
            if (it?.sku == premiumSku) {
                premiumInventory = it
                isAvailable = true
                Timber.d("premiumInventory saved")
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
                                                                            if (it != BillingResponse.OK) {
                                                                                Timber.d(
                                                                                        "Acknowledge response code: %d",
                                                                                        it
                                                                                )
                                                                            } else {
                                                                                Timber.d(
                                                                                        "Purchase (%s) acknowledged",
                                                                                        purchase.purchaseToken
                                                                                )
                                                                                makePremium(purchase.purchaseToken)
                                                                            }
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
        savePreference(tokenString)
        _isPremium = true
        premiumChanged.onNext(_isPremium)
    }

    private fun checkPurchased() {
        billing.purchasedInApps.subscribe({
                                              if (it.purchaseState == Purchase.PurchaseState.PURCHASED && it.productId == premiumSku) {
                                                  // TODO Add some validation to verify the user really purchased it somehow
                                                  // TODO: Make this work?
                                                  Timber.d("need to set isPremium to true")
                                                  if (preferenceToken != it.purchaseToken || !_isPremium) {
                                                      makePremium(it.purchaseToken)
                                                  }
                                              } else {
                                                  Timber.d("not showing as purchased")
                                                  Timber.d("packageName: %s", it.packageName)
                                                  Timber.d("productId: %s", it.productId)
                                                  Timber.d("purchaseToken: %s", it.purchaseToken)
                                                  Timber.d(
                                                          "purchaseTime: %s",
                                                          SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(
                                                                  it.purchaseTime
                                                          )
                                                  )
                                                  Timber.d("purchaseState: %d", it.purchaseState)
                                              }

                                          }, {
                                              // Something went wrong
                                              Timber.w(it)
                                          })
    }

    fun purchasePro() {
        if (premiumInventory == null) {
            Timber.w("Cannot initiate purchase yet.")
        }

        premiumInventory?.let { it ->
            billing.purchase(it).subscribe({ result ->
                                               Timber.d("Purchase result: %d", result.responseCode)
                                           }, { t ->
                                               Timber.e(t)
                                           })
        }
    }

    fun destroy() {
        if (purchaseSubscription != null && purchaseSubscription?.isDisposed == false) {
            purchaseSubscription?.dispose()
        }
        billing.destroy()
    }

    private fun loadPreference() {
        val prefs = activity.applicationContext.getSharedPreferences(PREFERENCE_FILE, MODE_PRIVATE)
        preferenceToken = prefs.getString(PREFERENCE_KEY, INVALID_TOKEN) ?: INVALID_TOKEN

        if (preferenceToken != INVALID_TOKEN) {
            // Will run a verify later.
            _isPremium = true
        }
    }

    private fun savePreference(purchaseToken: String) {
        preferenceToken = purchaseToken
        val prefs = activity.applicationContext.getSharedPreferences(PREFERENCE_FILE, MODE_PRIVATE)
        prefs.edit().putString(PREFERENCE_KEY, purchaseToken).apply()
    }
}