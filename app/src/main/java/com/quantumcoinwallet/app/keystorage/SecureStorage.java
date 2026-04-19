package com.quantumcoinwallet.app.keystorage;

import android.content.Context;
import android.util.Base64;

import com.quantumcoinwallet.app.bridge.QuantumCoinJSBridge;
import com.quantumcoinwallet.app.utils.PrefConnect;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecureStorage {

    private static final int SCRYPT_N = 262144;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SCRYPT_KEY_LEN = 32;
    private static final int SALT_SIZE = 32;
    private static final int IV_SIZE = 16;

    private static final String KEY_SALT = "SECURE_DERIVED_KEY_SALT";
    private static final String KEY_ENCRYPTED_MAIN_KEY = "SECURE_ENCRYPTED_MAIN_KEY";
    private static final String KEY_MAX_WALLET_INDEX = "SECURE_MAX_WALLET_INDEX";
    private static final String LEGACY_KEY_PASSWORD_VERIFIER = "SECURE_PASSWORD_VERIFIER";
    private static final String WALLET_PREFIX = "SECURE_WALLET_";

    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    private byte[] mainKey;
    private final QuantumCoinJSBridge bridge;

    public SecureStorage(QuantumCoinJSBridge bridge) {
        this.bridge = bridge;
    }

    public boolean isInitialized(Context ctx) {
        String salt = PrefConnect.readString(ctx, KEY_SALT, "");
        String encMainKey = PrefConnect.readString(ctx, KEY_ENCRYPTED_MAIN_KEY, "");
        return !salt.isEmpty() && !encMainKey.isEmpty();
    }

    public boolean isUnlocked() {
        return mainKey != null;
    }

    /**
     * First-time setup: generate random salt and mainKey, derive encryption key via Scrypt,
     * encrypt the mainKey, and store both salt and encrypted mainKey in SharedPreferences.
     * Must be called from a background thread.
     */
    public void createMainKey(Context ctx, String password) throws Exception {
        byte[] salt = new byte[SALT_SIZE];
        new SecureRandom().nextBytes(salt);
        String saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP);

        byte[] newMainKey = new byte[SCRYPT_KEY_LEN];
        new SecureRandom().nextBytes(newMainKey);

        byte[] derivedKey = scryptDerive(password, saltBase64);

        EncryptedPayload encrypted = encrypt(derivedKey, Base64.encodeToString(newMainKey, Base64.NO_WRAP));

        JSONObject encJson = new JSONObject();
        encJson.put("cipherText", encrypted.cipherTextBase64);
        encJson.put("iv", encrypted.ivBase64);

        PrefConnect.writeString(ctx, KEY_SALT, saltBase64);
        PrefConnect.writeString(ctx, KEY_ENCRYPTED_MAIN_KEY, encJson.toString());

        this.mainKey = newMainKey;
    }

    /**
     * Unlock: derive key from password + stored salt via Scrypt, decrypt the mainKey.
     * Must be called from a background thread.
     * @return true on success, false on wrong password
     */
    public boolean unlock(Context ctx, String password) {
        try {
            String saltBase64 = PrefConnect.readString(ctx, KEY_SALT, "");
            String encMainKeyJson = PrefConnect.readString(ctx, KEY_ENCRYPTED_MAIN_KEY, "");
            if (saltBase64.isEmpty() || encMainKeyJson.isEmpty()) return false;

            byte[] derivedKey = scryptDerive(password, saltBase64);

            JSONObject encJson = new JSONObject(encMainKeyJson);
            EncryptedPayload payload = new EncryptedPayload(
                    encJson.getString("cipherText"),
                    encJson.getString("iv"));

            String mainKeyBase64 = decrypt(derivedKey, payload);
            this.mainKey = Base64.decode(mainKeyBase64, Base64.NO_WRAP);

            try {
                String legacy = PrefConnect.readString(ctx, LEGACY_KEY_PASSWORD_VERIFIER, "");
                if (legacy != null && !legacy.isEmpty()) {
                    PrefConnect.getEditor(ctx).remove(LEGACY_KEY_PASSWORD_VERIFIER).commit();
                }
            } catch (Throwable ignore) { }

            return true;
        } catch (Exception e) {
            this.mainKey = null;
            return false;
        }
    }

    public void lock() {
        if (mainKey != null) {
            Arrays.fill(mainKey, (byte) 0);
            mainKey = null;
        }
    }

    /**
     * Build in-memory address maps by decrypting all wallets.
     * Returns {addressToIndex, indexToAddress} pair.
     * Must be called when unlocked and from a background thread if wallets are large.
     */
    public Map<String, String>[] buildWalletMaps(Context ctx) throws Exception {
        requireUnlocked();
        Map<String, String> addressToIndex = new HashMap<>();
        Map<String, String> indexToAddress = new HashMap<>();
        int maxIndex = getMaxWalletIndex(ctx);
        for (int i = 0; i <= maxIndex; i++) {
            String walletJson = loadWallet(ctx, i);
            if (walletJson != null) {
                JSONObject wallet = new JSONObject(walletJson);
                String address = wallet.getString("address");
                String indexStr = String.valueOf(i);
                addressToIndex.put(address, indexStr);
                indexToAddress.put(indexStr, address);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, String>[] result = new Map[]{addressToIndex, indexToAddress};
        return result;
    }

    public void setSecureItem(Context ctx, String key, String value) throws Exception {
        requireUnlocked();
        EncryptedPayload encrypted = encrypt(mainKey, value);
        JSONObject encJson = new JSONObject();
        encJson.put("cipherText", encrypted.cipherTextBase64);
        encJson.put("iv", encrypted.ivBase64);
        PrefConnect.writeString(ctx, key, encJson.toString());
    }

    public String getSecureItem(Context ctx, String key) throws Exception {
        requireUnlocked();
        String stored = PrefConnect.readString(ctx, key, "");
        if (stored.isEmpty()) return null;
        JSONObject encJson = new JSONObject(stored);
        EncryptedPayload payload = new EncryptedPayload(
                encJson.getString("cipherText"),
                encJson.getString("iv"));
        return decrypt(mainKey, payload);
    }

    public void removeSecureItem(Context ctx, String key) {
        PrefConnect.getEditor(ctx).remove(key).commit();
    }

    public void saveWallet(Context ctx, int index, String walletJson) throws Exception {
        setSecureItem(ctx, WALLET_PREFIX + index, walletJson);
    }

    public String loadWallet(Context ctx, int index) throws Exception {
        return getSecureItem(ctx, WALLET_PREFIX + index);
    }

    public int getMaxWalletIndex(Context ctx) {
        return PrefConnect.readInteger(ctx, KEY_MAX_WALLET_INDEX, -1);
    }

    public void setMaxWalletIndex(Context ctx, int index) {
        PrefConnect.writeInteger(ctx, KEY_MAX_WALLET_INDEX, index);
    }

    private void requireUnlocked() {
        if (mainKey == null) {
            throw new IllegalStateException("SecureStorage is locked");
        }
    }

    private byte[] scryptDerive(String password, String saltBase64) throws Exception {
        String resultJson = bridge.scryptDerive(password, saltBase64,
                SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_KEY_LEN);
        JSONObject json = new JSONObject(resultJson);
        JSONObject data = json.getJSONObject("data");
        String keyBase64 = data.getString("key");
        return Base64.decode(keyBase64, Base64.NO_WRAP);
    }

    private EncryptedPayload encrypt(byte[] key, String plaintext) throws Exception {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);

        SecretKeySpec keySpec = new SecretKeySpec(key, KEY_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] cipherBytes = cipher.doFinal(plaintext.getBytes("UTF-8"));

        return new EncryptedPayload(
                Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP));
    }

    private String decrypt(byte[] key, EncryptedPayload payload) throws Exception {
        byte[] iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP);
        byte[] cipherBytes = Base64.decode(payload.cipherTextBase64, Base64.NO_WRAP);

        SecretKeySpec keySpec = new SecretKeySpec(key, KEY_ALGORITHM);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] plainBytes = cipher.doFinal(cipherBytes);

        return new String(plainBytes, "UTF-8");
    }

    private static class EncryptedPayload {
        final String cipherTextBase64;
        final String ivBase64;

        EncryptedPayload(String cipherTextBase64, String ivBase64) {
            this.cipherTextBase64 = cipherTextBase64;
            this.ivBase64 = ivBase64;
        }
    }
}
