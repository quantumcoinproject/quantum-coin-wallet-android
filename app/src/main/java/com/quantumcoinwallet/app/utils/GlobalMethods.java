package com.quantumcoinwallet.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.CountDownTimer;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RawRes;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.model.AccountTokenSummary;
import com.quantumcoinwallet.app.model.BlockchainNetwork;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class GlobalMethods {

    public static Toast toast;

    public static String step = "[STEP]";
    public static String totalSteps = "[TOTAL_STEPS]";


    public static String HTTPS = "https://";

    // M-05: these identify the currently active blockchain network and are
    // read by worker threads (REST tasks, JS bridge, etc.) while being
    // written from the UI thread when the user switches network. Writes go
    // through setActiveNetwork() which takes the monitor and publishes a
    // consistent snapshot; reads see a coherent snapshot via volatile.
    public static volatile String SCAN_API_URL = null;
    public static volatile String RPC_ENDPOINT_URL = null;

    public static volatile String BLOCK_EXPLORER_URL = null;
    public static String BLOCK_EXPLORER_TX_HASH_URL =  "/txn/{txhash}";
    public static String BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL = "/account/{address}/txn/page";

    public static String DP_DOCS_URL = "https://quantumcoin.org/";

    //Network
    public static volatile String BLOCKCHAIN_NAME = null;
    public static volatile String NETWORK_ID = null;

    private static final Object ACTIVE_NETWORK_LOCK = new Object();

    /**
     * M-05: switch all active-network fields atomically. Callers (notably
     * {@code HomeActivity}) should funnel every network write through here
     * so workers reading the volatile fields see a consistent snapshot and
     * cannot observe mixed state across fields.
     */
    public static void setActiveNetwork(BlockchainNetwork network) {
        if (network == null) return;
        synchronized (ACTIVE_NETWORK_LOCK) {
            SCAN_API_URL = HTTPS + network.getScanApiDomain();
            RPC_ENDPOINT_URL = network.getRpcEndpoint();
            BLOCK_EXPLORER_URL = HTTPS + network.getBlockExplorerDomain();
            BLOCKCHAIN_NAME = network.getBlockchainName();
            NETWORK_ID = network.getNetworkId();
        }
    }

    public static String GAS_QCN_LIMIT = "21000";
    public static String GAS_TOKEN_LIMIT = "84000";

    // Token list cache populated from the scan API for the currently active wallet.
    // CURRENT_WALLET_TOKEN_LIST_ADDRESS tracks which address the cache belongs to,
    // so HomeActivity can invalidate on wallet switch / network change.
    public static volatile List<AccountTokenSummary> CURRENT_WALLET_TOKEN_LIST = new ArrayList<>();
    public static volatile String CURRENT_WALLET_TOKEN_LIST_ADDRESS = null;

    public static int DURATION = 20;
    public static int MINIMUM_PASSWORD_LENGTH = 12;
    public static int ADDRESS_LENGTH = 66;
    public static String ADDRESS_START_PREFIX = "0x";
    public static int ethAddressSeedDrivePathCount = 100;
    public static String COIN_SYMBOL = "";

   // public static int TOAST_SHOW_LENGTH = 1;

    //public static Context context = null;

    //String Values to be Used in App
    public static final String downloadDirectory = "quantumcoinWallet";

    // Wordlist cache fetched once at startup via the seed-words SDK WebView bridge.
    // ALL_SEED_WORDS powers the autocomplete adapter; SEED_WORD_SET supports O(1)
    // per-word validation without crossing the bridge for every keystroke.
    public static volatile ArrayList<String> ALL_SEED_WORDS = new ArrayList<>();
    public static volatile HashSet<String> SEED_WORD_SET = new HashSet<>();
    public static volatile boolean seedLoaded = false;

    public static String LocaleLanguage(Context context, String languageKey){
        if (languageKey.equals("en")) {
            return readRawResource(context, R.raw.en_us);
        }
        return readRawResource(context, R.raw.en_us);
    }
    public static List<BlockchainNetwork> BlockChainNetworkRead(Context context) throws JSONException {
        String blockchainJsonString = PrefConnect.readString(context, PrefConnect.BLOCKCHAIN_NETWORK_LIST, "");
        if (TextUtils.isEmpty(blockchainJsonString)) {
            blockchainJsonString = readRawResource(context, R.raw.blockchain_networks);
        }

        Object parsed = new JSONTokener(blockchainJsonString).nextValue();
        if (!(parsed instanceof JSONObject)) {
            throw new JSONException("network list root is not a JSON object");
        }
        JSONObject jo = (JSONObject) parsed;
        JSONArray jsonArray = jo.optJSONArray("networks");
        if (jsonArray == null) {
            throw new JSONException("network list missing 'networks' array");
        }

        List<BlockchainNetwork> blockChainNetworks = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject entry = jsonArray.optJSONObject(i);
            if (entry == null) continue;

            String scanApiDomain = entry.optString("scanApiDomain", "").trim();
            String rpcEndpoint = entry.optString("rpcEndpoint", "").trim();
            String blockExplorerDomain = entry.optString("blockExplorerDomain", "").trim();
            String blockchainName = entry.optString("blockchainName", "").trim();
            String networkId = String.valueOf(entry.opt("networkId")).trim();

            BlockchainNetwork blockchainNetwork = new BlockchainNetwork();
            blockchainNetwork.setScanApiDomain(scanApiDomain);
            blockchainNetwork.setRpcEndpoint(rpcEndpoint);
            blockchainNetwork.setBlockExplorerDomain(blockExplorerDomain);
            blockchainNetwork.setBlockchainName(blockchainName);
            blockchainNetwork.setNetworkId(networkId);
            blockChainNetworks.add(blockchainNetwork);
        }
        return blockChainNetworks;
    }

    public static String readRawResource(Context context,  @RawRes int res) {
        return readStream(context.getResources().openRawResource(res));
    }
    private static String readStream(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /*
    public static void LocaleLanguage(Context context, String TAG, String languagekey){
        try {
            if (TextUtils.isEmpty(languagekey)) {
                languagekey = "en";
            }


            Locale locale = new Locale(languagekey);
            Locale.setDefault(locale);
            android.content.res.Configuration config = new Configuration();
            config.locale = locale;

            context.getResources().updateConfiguration(config,
                    context.getResources().getDisplayMetrics());
        }catch(Exception ex){
            GlobalMethods.ExceptionError(context, TAG, ex);
        }
    }
*/

    public static boolean IsNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }
        NetworkInfo networkInfo = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            return false;
        }
        return true;
    }

    //api exception error
    public static void ExceptionError(Context context, String tag, Exception e) {
        String message = e != null && e.getMessage() != null ? e.getMessage() : "";
        ShowErrorDialog(context, tag, message);
        //Firebase.CrashLogcat(tag, e.toString());
    }

    public static void ShowErrorDialog(Context context, String title, String message) {
        // M-03: always route through Timber so ReleaseTree can redact/strip.
        // Only write debug details when explicitly running a debug build.
        if (com.quantumcoinwallet.app.BuildConfig.DEBUG) {
            timber.log.Timber.tag("QuantumCoinWallet").w("%s: %s", title, message);
        }
        if (context instanceof Activity && !((Activity) context).isFinishing()) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setCancelable(true)
                    .show();
        } else {
            ShowToast(context, title + ": " + message);
        }
    }

    //Api exception error onFailure(ApiException e)
    public static boolean ApiExceptionSourceCodeBoolean(int code) {
        boolean checkExcaption = true;
        switch (code) {
            case 401: //Unauthorized
                break;
            case 404: //NotFound
                break;
            case 406: //NotAcceptable (Invalid header)
                break;
            case 409: //Conflict (Try again)
                break;
            case 417: //ExpectationFailed(captha verification)
                break;
            default:
                checkExcaption = false;
                break;
        }
        return checkExcaption;
    }

    //api exception error route
    public static void ApiExceptionSourceCodeRoute(Context context, int code,
                                                   String displayerrormessage, String exceptionError) {
        if (!(context instanceof Activity)) {
            GlobalMethods.ShowToast(context, displayerrormessage);
            return;
        }
        Activity activity = (Activity) context;

        switch (code) {
            case 401: //Unauthorized
                GlobalMethods.ShowToast(context, activity.getString(R.string.code_401));
                break;
            case 404: //NotFound
                GlobalMethods.ShowToast(context, activity.getString(R.string.code_404));
                break;
            case 406: //NotAcceptable (Invalid header)
                GlobalMethods.ShowToast(context, activity.getString(R.string.code_406));
                break;
            case 409: //Conflict (Try again)
                GlobalMethods.ShowToast(context, activity.getString(R.string.code_409));
                break;
            case 417: //ExpectationFailed(captha verification)
                GlobalMethods.ShowToast(context, activity.getString(R.string.code_417));
                break;
            default:
                GlobalMethods.ShowToast(context, displayerrormessage);
                break;
        }

        //Firebase.CrashLog(exceptionError);
    }

    //Exception Offline Or exception error
    public static void OfflineOrExceptionError(Context context, LinearLayout layout, ImageView image,
                                               TextView textTitle, TextView textSubTitle,
                                               boolean isNetworkAvailable){
        //Activity activity = (Activity) context;

        layout.setVisibility(View.VISIBLE);

        if(isNetworkAvailable==false)
        {
            image.setImageResource(R.drawable.noconnection);
            textTitle.setText(context.getString(R.string.general_connect_internet));
            textSubTitle.setText(context.getString(R.string.general_offline_access));
        }
        else
        {
            image.setImageResource(R.drawable.noconnection);
            textTitle.setText(context.getString(R.string.offline_exception_error_title));
            textSubTitle.setText(context.getString(R.string.offline_exception_error_subtitle));
        }
    }


    public static void ShowToast(final Context context, final String message) {
        if (context == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ShowToast(context, message);
                }
            });
            return;
        }
        // Set the toast and duration
        int toastDurationInMilliSeconds = 600;
        toast = Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG);

        // Set the countdown to display the toast
        CountDownTimer toastCountDown;
        Toast finalToast = toast;
        toastCountDown = new CountDownTimer(toastDurationInMilliSeconds, 1) {
            public void onTick(long millisUntilFinished) {
                finalToast.show();
            }
            public void onFinish() {
                finalToast.cancel();
            }
        };

        // Show the toast and starts the countdown
        toast.show();
        toastCountDown.start();
    }

    public static int[] GetIntDataArrayByString(String d) {
        String[] data = d.split(",");
        int[] intData = new int[data.length];
        for(int i = 0;i < intData.length;i++)
        {
            intData[i] = Integer.parseInt(data[i]);
        }
        return intData;
    }

    public static int[] GetIntDataArrayByStringArray(String[] string) {
        int number[] = new int[string.length];

        for (int i = 0; i < string.length; i++) {
            number[i] = Integer.parseInt(string[i]); // error here
        }
        return number;
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static byte[] HexStringToByteArray(String s) {
        byte data[] = new byte[s.length()/2];
        for(int i=0;i < s.length();i+=2) {
            data[i/2] = (Integer.decode("0x"+s.charAt(i)+s.charAt(i+1))).byteValue();
        }
        return data;
    }
}

