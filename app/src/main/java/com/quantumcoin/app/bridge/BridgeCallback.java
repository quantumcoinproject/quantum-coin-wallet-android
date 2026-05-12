package com.quantumcoin.app.bridge;

public interface BridgeCallback {
    void onResult(String jsonResult);
    void onError(String error);
}
