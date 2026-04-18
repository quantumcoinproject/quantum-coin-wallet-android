package com.quantumcoinwallet.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.CountDownTimer;
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
import com.quantumcoinwallet.app.seedwords.SeedWords;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class GlobalMethods {

    public static Toast toast;

    public static String step = "[STEP]";
    public static String totalSteps = "[TOTAL_STEPS]";


    public static String HTTPS = "https://";

    //URL
    public static String SCAN_API_URL = null;
    public static String RPC_ENDPOINT_URL = null;

    public static String BLOCK_EXPLORER_URL = null;
    public static String BLOCK_EXPLORER_TX_HASH_URL =  "/txn/{txhash}";
    public static String BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL = "/account/{address}/txn/page";

    public static String DP_DOCS_URL = "https://quantumcoin.org/";

    //Network
    public static String BLOCKCHAIN_NAME = null;
    public static String NETWORK_ID = null;

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

    public static SeedWords seedWords;
    public static boolean seedLoaded = false;

    public static String LocaleLanguage(Context context, String languageKey){
        if (languageKey.equals("en")) {
            return readRawResource(context, R.raw.en_us);
        }
        return readRawResource(context, R.raw.en_us);
    }
    public static List<BlockchainNetwork> BlockChainNetworkRead(Context context) throws JSONException {
        String blockchainJsonString = "";
        blockchainJsonString = PrefConnect.readString(context, PrefConnect.BLOCKCHAIN_NETWORK_LIST, "");
        if(blockchainJsonString=="") {
            blockchainJsonString=readRawResource(context, R.raw.blockchain_networks);
        }

        JsonObject jo = new JsonParser().parse(blockchainJsonString).getAsJsonObject();
        JsonArray jsonArray = jo.getAsJsonArray("networks");

        List<BlockchainNetwork> blockChainNetworks = new ArrayList<>();
        for (int i=0; i < jsonArray.size(); i++) {

            String scanApiDomain = jsonArray.get(i).getAsJsonObject().get("scanApiDomain").toString().replace("\"", "").replace("\'", "");
            String rpcEndpoint = jsonArray.get(i).getAsJsonObject().get("rpcEndpoint").toString().replace("\"", "").replace("\'", "");
            String blockExplorerDomain = jsonArray.get(i).getAsJsonObject().get("blockExplorerDomain").toString().replace("\"", "").replace("\'", "");
            String blockchainName = jsonArray.get(i).getAsJsonObject().get("blockchainName").toString().replace("\"", "").replace("\'", "");
            String networkId = jsonArray.get(i).getAsJsonObject().get("networkId").toString().replace("\"", "").replace("\'", "");

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
        ShowToast(context, tag + " : " + e.getMessage());
        //Firebase.CrashLogcat(tag, e.toString());
    }

    public static void ShowErrorDialog(Context context, String title, String message) {
        android.util.Log.e("QuantumCoinWallet", title + ": " + message);
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


    public static void ShowToast(Context context, String message) {
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

