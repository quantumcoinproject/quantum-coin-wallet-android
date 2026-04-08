package com.quantumcoinwallet.app.bridge;

public interface BridgeCallback {
    void onResult(String jsonResult);
    void onError(String error);
}
