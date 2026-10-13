package com.alexandercbgm.ugomonikota;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing for the one-time product "unlock_full" (levels 3-8).
 * JavaScript calls UgomoniStore.buy() / restore() / refresh(); results go back to window.ugomoniStore:
 * onPrice(text), onOwned(bool), onPending(), onError("cancel" | "unavailable" | "none" | "fail").
 */
public class StoreBridge {
    static final String PRODUCT_ID = "unlock_full";

    private final Activity activity;
    private final WebView web;
    private final BillingClient client;
    private ProductDetails product;
    private boolean restoring;

    StoreBridge(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        client = BillingClient.newBuilder(activity)
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        connect();
    }

    private void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    loadProduct();
                    checkOwned();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call
            }
        });
    }

    private void loadProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        client.queryProductDetailsAsync(params, (r, result) -> {
            List<ProductDetails> list = result.getProductDetailsList();
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list.isEmpty()) return;
            product = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
            if (offer != null) js("onPrice", JSONObject.quote(offer.getFormattedPrice()));
        });
    }

    /** Owned purchases from Play's cache: unlocks after reinstall, relocks after a refund. */
    private void checkOwned() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (r, purchases) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        if (restoring) js("onError", "'unavailable'");
                        restoring = false;
                        return;
                    }
                    boolean owned = false;
                    for (Purchase p : purchases) owned |= handle(p);
                    if (restoring && !owned) js("onError", "'none'");
                    else js("onOwned", owned ? "true" : "false");
                    restoring = false;
                });
    }

    private void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            boolean owned = false;
            for (Purchase p : purchases) owned |= handle(p);
            if (owned) js("onOwned", "true");
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            checkOwned();
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            js("onError", "'cancel'");
        } else if (code == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
                || code == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
            js("onError", "'unavailable'");
        } else {
            js("onError", "'fail'");
        }
    }

    /** True when the purchase grants the full game. Unacknowledged purchases are refunded by Play after 3 days. */
    private boolean handle(Purchase p) {
        if (!p.getProducts().contains(PRODUCT_ID)) return false;
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            js("onPending", "");
            return false;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return false;
        if (!p.isAcknowledged()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                    ack -> { });
        }
        return true;
    }

    private void js(String fn, String arg) {
        web.post(() -> web.evaluateJavascript("window.ugomoniStore&&ugomoniStore." + fn + "(" + arg + ")", null));
    }

    @JavascriptInterface
    public void buy() {
        activity.runOnUiThread(() -> {
            if (product == null) {
                js("onError", "'unavailable'");
                if (client.isReady()) loadProduct(); else connect();
                return;
            }
            BillingFlowParams params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
                    .build();
            BillingResult r = client.launchBillingFlow(activity, params);
            if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) checkOwned();
            else if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) js("onError", "'unavailable'");
        });
    }

    @JavascriptInterface
    public void restore() {
        activity.runOnUiThread(() -> {
            restoring = true;
            if (client.isReady()) checkOwned(); else connect();
        });
    }

    /** Called from the paywall and on resume: picks up purchases made while the app was away. */
    @JavascriptInterface
    public void refresh() {
        activity.runOnUiThread(() -> {
            if (!client.isReady()) return;
            if (product == null) loadProduct();
            checkOwned();
        });
    }

    void close() {
        client.endConnection();
    }
}
