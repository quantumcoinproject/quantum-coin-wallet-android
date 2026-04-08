package com.quantumcoinwallet.app.bridge;

import android.os.Handler;
import android.os.Looper;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class QuantumCoinJSBridge {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private final WebViewManager webViewManager;
    private final Handler mainHandler;

    public QuantumCoinJSBridge(WebViewManager webViewManager) {
        this.webViewManager = webViewManager;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ---- Async methods ----

    public void initializeAsync(int chainId, String rpcEndpoint, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.initialize('" + requestId + "', "
                + chainId + ", '" + escapeForJs(rpcEndpoint) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void initializeOfflineAsync(BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.initializeOffline('" + requestId + "')";
        evaluateOnMainThread(jsCall);
    }

    public void createRandomSeedAsync(int keyType, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.createRandomSeed('" + requestId + "', " + keyType + ")";
        evaluateOnMainThread(jsCall);
    }

    public void walletFromSeedAsync(int[] seedArray, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.walletFromSeed('" + requestId + "', '"
                + escapeForJs(intArrayToJson(seedArray)) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void walletFromPhraseAsync(String[] words, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.walletFromPhrase('" + requestId + "', '"
                + escapeForJs(stringArrayToJson(words)) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void walletFromKeysAsync(String privKeyBase64, String pubKeyBase64,
                                    BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.walletFromKeys('" + requestId + "', '"
                + escapeForJs(privKeyBase64) + "', '"
                + escapeForJs(pubKeyBase64) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void sendTransactionAsync(String privKeyBase64, String pubKeyBase64,
                                     String toAddress, String valueWei,
                                     String gasLimit, String rpcEndpoint,
                                     int chainId, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.sendTransaction('" + requestId + "', '"
                + escapeForJs(privKeyBase64) + "', '"
                + escapeForJs(pubKeyBase64) + "', '"
                + escapeForJs(toAddress) + "', '"
                + escapeForJs(valueWei) + "', '"
                + escapeForJs(gasLimit) + "', '"
                + escapeForJs(rpcEndpoint) + "', "
                + chainId + ")";
        evaluateOnMainThread(jsCall);
    }

    public void isValidAddressAsync(String address, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.isValidAddress('" + requestId + "', '"
                + escapeForJs(address) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void computeAddressAsync(String pubKeyBase64, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.computeAddress('" + requestId + "', '"
                + escapeForJs(pubKeyBase64) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void formatEtherAsync(String weiValue, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.formatEther('" + requestId + "', '"
                + escapeForJs(weiValue) + "')";
        evaluateOnMainThread(jsCall);
    }

    public void parseEtherAsync(String etherValue, BridgeCallback callback) {
        String requestId = UUID.randomUUID().toString();
        webViewManager.registerCallback(requestId, callback);
        String jsCall = "bridge.parseEther('" + requestId + "', '"
                + escapeForJs(etherValue) + "')";
        evaluateOnMainThread(jsCall);
    }

    // ---- Blocking wrappers ----

    public String initialize(int chainId, String rpcEndpoint) {
        return blockingCall(cb -> initializeAsync(chainId, rpcEndpoint, cb));
    }

    public String initializeOffline() {
        return blockingCall(this::initializeOfflineAsync);
    }

    public String createRandomSeed(int keyType) {
        return blockingCall(cb -> createRandomSeedAsync(keyType, cb));
    }

    public String walletFromSeed(int[] seedArray) {
        return blockingCall(cb -> walletFromSeedAsync(seedArray, cb));
    }

    public String walletFromPhrase(String[] words) {
        return blockingCall(cb -> walletFromPhraseAsync(words, cb));
    }

    public String walletFromKeys(String privKeyBase64, String pubKeyBase64) {
        return blockingCall(cb -> walletFromKeysAsync(privKeyBase64, pubKeyBase64, cb));
    }

    public String sendTransaction(String privKeyBase64, String pubKeyBase64,
                                  String toAddress, String valueWei,
                                  String gasLimit, String rpcEndpoint, int chainId) {
        return blockingCall(cb -> sendTransactionAsync(
                privKeyBase64, pubKeyBase64, toAddress, valueWei,
                gasLimit, rpcEndpoint, chainId, cb));
    }

    public String isValidAddress(String address) {
        return blockingCall(cb -> isValidAddressAsync(address, cb));
    }

    public String computeAddress(String pubKeyBase64) {
        return blockingCall(cb -> computeAddressAsync(pubKeyBase64, cb));
    }

    public String formatEther(String weiValue) {
        return blockingCall(cb -> formatEtherAsync(weiValue, cb));
    }

    public String parseEther(String etherValue) {
        return blockingCall(cb -> parseEtherAsync(etherValue, cb));
    }

    // ---- Internal helpers ----

    private void evaluateOnMainThread(String jsCall) {
        mainHandler.post(() -> webViewManager.evaluateJavascript(jsCall, null));
    }

    private String blockingCall(AsyncInvoker invoker) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException(
                    "Blocking bridge call must not be invoked on the main thread");
        }

        if (!webViewManager.waitUntilReady(DEFAULT_TIMEOUT_SECONDS)) {
            throw new RuntimeException("Bridge WebView did not become ready in time");
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        invoker.invoke(new BridgeCallback() {
            @Override
            public void onResult(String jsonResult) {
                resultRef.set(jsonResult);
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                        "Bridge call timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bridge call interrupted", e);
        }

        if (errorRef.get() != null) {
            throw new RuntimeException("Bridge call failed: " + errorRef.get());
        }
        return resultRef.get();
    }

    static String escapeForJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    static String intArrayToJson(int[] arr) {
        if (arr == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    static String stringArrayToJson(String[] arr) {
        if (arr == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escapeForJs(arr[i])).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    @FunctionalInterface
    private interface AsyncInvoker {
        void invoke(BridgeCallback callback);
    }
}
