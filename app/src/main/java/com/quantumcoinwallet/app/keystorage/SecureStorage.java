package com.quantumcoinwallet.app.keystorage;

import android.content.Context;
import android.util.Base64;

import com.quantumcoinwallet.app.bridge.QuantumCoinJSBridge;
import com.quantumcoinwallet.app.utils.PrefConnect;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Password-encrypted wallet storage.
 *
 * <p>Cryptographic design (MF-04, MF-14):</p>
 * <ul>
 *     <li>Password is stretched with Scrypt (N=2^18, r=8, p=1, 32-byte
 *         output). The derived key only ever encrypts the random 32-byte
 *         {@code mainKey}.</li>
 *     <li>{@code mainKey} encrypts every per-wallet blob stored under
 *         {@code SECURE_WALLET_*}.</li>
 *     <li>All AES operations are AES-256-GCM with a fresh random 12-byte
 *         IV per encryption and a 128-bit auth tag. Ciphertexts are
 *         tagged with {@code "v":2}. Wrong password / tampered blob
 *         surfaces as a uniform {@link AEADBadTagException} from the
 *         decrypt side so callers cannot padding-oracle the store.</li>
 *     <li>The scrypt-derived key and decoded {@code mainKey} copies are
 *         zeroized as soon as they have been consumed; {@link #lock()}
 *         wipes the in-memory {@code mainKey}.</li>
 * </ul>
 *
 * <p><b>Why the master key is NOT wrapped by AndroidKeyStore (H-01,
 * architectural decision):</b></p>
 * <p>The encrypted {@code mainKey} and all per-wallet blobs must remain
 * <i>portable across devices</i> so users can recover their wallets when
 * they migrate to a new phone or restore from a cloud/device backup.
 * AndroidKeyStore-wrapped material is bound to the originating device's
 * TEE/StrongBox and cannot be moved off-device, which would break the
 * product's phone-migration and opt-in cloud-backup story.</p>
 *
 * <p>Users opt into having this data leave the device through explicit UI:</p>
 * <ul>
 *     <li>The backup-password prompt in
 *         {@code com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog}.</li>
 *     <li>The backup toggle in
 *         {@code com.quantumcoinwallet.app.view.fragment.BackupSettingsFragment}.</li>
 *     <li>The system backup agent at
 *         {@code com.quantumcoinwallet.app.backup.QuantumCoinBackupAgent} plus
 *         the {@code allowBackup} / {@code android:fullBackupContent}
 *         configuration in {@code AndroidManifest.xml} and
 *         {@code res/xml/backup_rules.xml} / {@code data_extraction_rules.xml}.</li>
 * </ul>
 *
 * <p>The trade-off (an offline scrypt attack is possible if a rooted attacker
 * exfiltrates the encrypted blob) is accepted because key portability is a
 * product requirement. Scrypt parameters (N=2^18, r=8, p=1) are tuned to
 * make that offline attack expensive against anything but a trivial password.</p>
 */
public class SecureStorage {

    private static final int SCRYPT_N = 262144;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SCRYPT_KEY_LEN = 32;
    private static final int SALT_SIZE = 32;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final String KEY_SALT = "SECURE_DERIVED_KEY_SALT";
    private static final String KEY_ENCRYPTED_MAIN_KEY = "SECURE_ENCRYPTED_MAIN_KEY";
    private static final String KEY_MAX_WALLET_INDEX = "SECURE_MAX_WALLET_INDEX";
    private static final String WALLET_PREFIX = "SECURE_WALLET_";

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
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
        try {
            EncryptedPayload encrypted = encrypt(derivedKey, newMainKey);

            JSONObject encJson = new JSONObject();
            encJson.put("v", 2);
            encJson.put("cipherText", encrypted.cipherTextBase64);
            encJson.put("iv", encrypted.ivBase64);

            PrefConnect.writeString(ctx, KEY_SALT, saltBase64);
            PrefConnect.writeString(ctx, KEY_ENCRYPTED_MAIN_KEY, encJson.toString());

            this.mainKey = newMainKey;
        } finally {
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    /**
     * Unlock: derive key from password + stored salt via Scrypt, decrypt the mainKey.
     * Must be called from a background thread.
     * @return true on success, false on wrong password
     */
    public boolean unlock(Context ctx, String password) {
        byte[] derivedKey = null;
        try {
            String saltBase64 = PrefConnect.readString(ctx, KEY_SALT, "");
            String encMainKeyJson = PrefConnect.readString(ctx, KEY_ENCRYPTED_MAIN_KEY, "");
            if (saltBase64.isEmpty() || encMainKeyJson.isEmpty()) return false;

            derivedKey = scryptDerive(password, saltBase64);

            JSONObject encJson = new JSONObject(encMainKeyJson);
            EncryptedPayload payload = new EncryptedPayload(
                    encJson.getString("cipherText"),
                    encJson.getString("iv"));

            byte[] mainKeyBytes;
            try {
                mainKeyBytes = decryptBytes(derivedKey, payload);
            } catch (AEADBadTagException bad) {
                return false;
            }
            this.mainKey = mainKeyBytes;
            return true;
        } catch (Exception e) {
            this.mainKey = null;
            return false;
        } finally {
            if (derivedKey != null) Arrays.fill(derivedKey, (byte) 0);
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
        byte[] plain = value.getBytes(StandardCharsets.UTF_8);
        try {
            EncryptedPayload encrypted = encrypt(mainKey, plain);
            JSONObject encJson = new JSONObject();
            encJson.put("v", 2);
            encJson.put("cipherText", encrypted.cipherTextBase64);
            encJson.put("iv", encrypted.ivBase64);
            PrefConnect.writeString(ctx, key, encJson.toString());
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public String getSecureItem(Context ctx, String key) throws Exception {
        requireUnlocked();
        String stored = PrefConnect.readString(ctx, key, "");
        if (stored.isEmpty()) return null;
        JSONObject encJson = new JSONObject(stored);
        EncryptedPayload payload = new EncryptedPayload(
                encJson.getString("cipherText"),
                encJson.getString("iv"));
        byte[] plain = decryptBytes(mainKey, payload);
        try {
            return new String(plain, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
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

    /**
     * L-01: wraps the WebView-hosted scrypt call and converts the result
     * straight into a {@code byte[]} key. The intermediate JSON String
     * and base64 String cannot be zeroized (Java String immutability),
     * so we hold no extra references beyond the minimum required and
     * null them out before returning so GC can reclaim them promptly.
     */
    private byte[] scryptDerive(String password, String saltBase64) throws Exception {
        String resultJson = null;
        String keyBase64 = null;
        byte[] derived = null;
        try {
            resultJson = bridge.scryptDerive(password, saltBase64,
                    SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_KEY_LEN);
            JSONObject json = new JSONObject(resultJson);
            JSONObject data = json.getJSONObject("data");
            keyBase64 = data.getString("key");
            derived = Base64.decode(keyBase64, Base64.NO_WRAP);
            return derived;
        } catch (Exception e) {
            if (derived != null) Arrays.fill(derived, (byte) 0);
            throw e;
        } finally {
            resultJson = null;
            keyBase64 = null;
        }
    }

    private EncryptedPayload encrypt(byte[] key, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_SIZE];
        new SecureRandom().nextBytes(iv);

        SecretKeySpec keySpec = new SecretKeySpec(key, KEY_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] cipherBytes = cipher.doFinal(plaintext);

        return new EncryptedPayload(
                Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP));
    }

    private byte[] decryptBytes(byte[] key, EncryptedPayload payload) throws GeneralSecurityException {
        byte[] iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP);
        byte[] cipherBytes = Base64.decode(payload.cipherTextBase64, Base64.NO_WRAP);

        if (iv.length != GCM_IV_SIZE) {
            throw new AEADBadTagException("bad iv length");
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, KEY_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        return cipher.doFinal(cipherBytes);
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
