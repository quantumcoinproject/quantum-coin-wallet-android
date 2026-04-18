package com.quantumcoinwallet.app.viewmodel;

import android.content.Context;

import com.quantumcoinwallet.app.bridge.BridgeCallback;
import com.quantumcoinwallet.app.bridge.QuantumCoinJSBridge;
import com.quantumcoinwallet.app.bridge.WebViewManager;
import com.quantumcoinwallet.app.entity.KeyServiceException;
import com.quantumcoinwallet.app.interact.KeyInteract;
import com.quantumcoinwallet.app.keystorage.IKeyStore;
import com.quantumcoinwallet.app.keystorage.KeyStore;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.services.IKeyService;
import com.quantumcoinwallet.app.services.KeyService;
import com.quantumcoinwallet.app.utils.CoinUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import androidx.lifecycle.ViewModel;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KeyViewModel extends ViewModel {

    private final KeyInteract keyInteract;
    private static QuantumCoinJSBridge bridgeInstance;
    private static SecureStorage secureStorageInstance;

    public KeyViewModel() {
        IKeyStore keyStore = new KeyStore();
        keyInteract = new KeyInteract(new KeyService(bridgeInstance), keyStore);
    }

    public static void initBridge(Context context) {
        WebViewManager webViewManager = WebViewManager.getInstance(context);
        bridgeInstance = new QuantumCoinJSBridge(webViewManager);
        secureStorageInstance = new SecureStorage(bridgeInstance);
    }

    public static QuantumCoinJSBridge getBridge() { return bridgeInstance; }
    public static SecureStorage getSecureStorage() { return secureStorageInstance; }

    // --- New bridge methods ---
    public void createRandomSeed(int keyType, BridgeCallback callback) { keyInteract.createRandomSeed(keyType, callback); }
    public void walletFromPhrase(String[] words, BridgeCallback callback) { keyInteract.walletFromPhrase(words, callback); }
    public void walletFromSeed(int[] seedArray, BridgeCallback callback) { keyInteract.walletFromSeed(seedArray, callback); }
    public void walletFromKeys(String privKeyBase64, String pubKeyBase64, BridgeCallback callback) { keyInteract.walletFromKeys(privKeyBase64, pubKeyBase64, callback); }
    public void sendTransaction(String privKeyBase64, String pubKeyBase64, String toAddress, String valueWei, String gasLimit, String rpcEndpoint, int chainId, boolean advancedSigningEnabled, BridgeCallback callback) { keyInteract.sendTransaction(privKeyBase64, pubKeyBase64, toAddress, valueWei, gasLimit, rpcEndpoint, chainId, advancedSigningEnabled, callback); }
    public void sendTokenTransaction(String privKeyBase64, String pubKeyBase64, String contractAddress, String toAddress, String amountWei, String gasLimit, String rpcEndpoint, int chainId, boolean advancedSigningEnabled, BridgeCallback callback) { keyInteract.sendTokenTransaction(privKeyBase64, pubKeyBase64, contractAddress, toAddress, amountWei, gasLimit, rpcEndpoint, chainId, advancedSigningEnabled, callback); }
    public void isValidAddress(String address, BridgeCallback callback) { keyInteract.isValidAddress(address, callback); }
    public void initializeOffline(BridgeCallback callback) { keyInteract.initializeOffline(callback); }
    public void initialize(int chainId, String rpcEndpoint, BridgeCallback callback) { keyInteract.initialize(chainId, rpcEndpoint, callback); }

    // Blocking variants for background threads
    public String createRandomSeedBlocking(int keyType) { return keyInteract.createRandomSeedBlocking(keyType); }
    public String walletFromPhraseBlocking(String[] words) { return keyInteract.walletFromPhraseBlocking(words); }
    public String walletFromSeedBlocking(int[] seedArray) { return keyInteract.walletFromSeedBlocking(seedArray); }
    public String initializeOfflineBlocking() { return keyInteract.initializeOfflineBlocking(); }
    public String sendTransactionBlocking(String privKeyBase64, String pubKeyBase64, String toAddress, String valueWei, String gasLimit, String rpcEndpoint, int chainId, boolean advancedSigningEnabled) { return keyInteract.sendTransactionBlocking(privKeyBase64, pubKeyBase64, toAddress, valueWei, gasLimit, rpcEndpoint, chainId, advancedSigningEnabled); }
    public String sendTokenTransactionBlocking(String privKeyBase64, String pubKeyBase64, String contractAddress, String toAddress, String amountWei, String gasLimit, String rpcEndpoint, int chainId, boolean advancedSigningEnabled) { return keyInteract.sendTokenTransactionBlocking(privKeyBase64, pubKeyBase64, contractAddress, toAddress, amountWei, gasLimit, rpcEndpoint, chainId, advancedSigningEnabled); }

    // --- Wei conversion (pure Java, main-thread safe) ---
    public String getWeiToDogeProtocol(String value) {
        return CoinUtils.formatWei(value);
    }

    // --- Encryption/decryption (unchanged) ---
    public boolean encryptDataByString(Context context, String key, String password, String passwordSHA256) {
        return keyInteract.encryptDataByAccount(context, key, password, passwordSHA256);
    }

    public String decryptDataByString(Context context, String key, String password) throws InvalidKeyException, KeyServiceException {
        byte[] byteArray = keyInteract.decryptDataByAccount(context, key, password);
        return new String(byteArray);
    }

    public boolean encryptDataByAccount(Context context, String key, String password, String[] keyPair) {
        Gson gson = new Gson();
        List<String> textList = new ArrayList<>(Arrays.asList(keyPair));
        String jsonText = gson.toJson(textList);
        return keyInteract.encryptDataByAccount(context, key, password, jsonText);
    }

    public String[] decryptDataByAccount(Context context, String key, String password) throws InvalidKeyException, KeyServiceException {
        byte[] byteArray = keyInteract.decryptDataByAccount(context, key, password);
        String jsonString = new String(byteArray);
        List<String> dataList = Arrays.asList(new GsonBuilder().create().fromJson(jsonString, String[].class));
        String[] data = new String[dataList.size()];
        data = dataList.toArray(data);
        return data;
    }
}
