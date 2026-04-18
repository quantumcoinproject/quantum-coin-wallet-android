package com.quantumcoinwallet.app.view.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.model.AccountTokenListResponse;
import com.quantumcoinwallet.app.api.read.model.AccountTokenSummary;
import com.quantumcoinwallet.app.api.read.model.BalanceResponse;
import com.quantumcoinwallet.app.asynctask.read.AccountBalanceRestTask;
import com.quantumcoinwallet.app.asynctask.read.ListAccountTokensRestTask;
import com.quantumcoinwallet.app.bridge.BridgeCallback;
import com.quantumcoinwallet.app.entity.KeyServiceException;
import com.quantumcoinwallet.app.entity.ServiceException;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.CoinUtils;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

public class SendFragment extends Fragment  {
    private static final String TAG = "SendFragment";

    private int retryStatus = 0;
    private int sendButtonStatus = 0;

    private LinearLayout linerLayoutOffline;
    private ImageView imageViewRetry;
    private TextView textViewTitleRetry;
    private TextView textViewSubTitleRetry;

    private SendFragment.OnSendCompleteListener mSendListener;
    private KeyViewModel keyViewModel = new KeyViewModel();
    private JsonViewModel jsonViewModel;

    // Asset selection state. When selectedContract == null the native Q asset is active.
    private String selectedContract;
    private String selectedSymbol;
    private int selectedDecimals = 18;
    private String selectedTokenBalanceWei;
    private List<AccountTokenSummary> tokenOptions = new ArrayList<>();

    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private static final int REQUEST_CAMERA_PERMISSION = 201;

    public static SendFragment newInstance() {
        SendFragment fragment = new SendFragment();
        return fragment;
    }

    public SendFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.send_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            String languageKey = getArguments().getString("languageKey");
            String walletAddress = getArguments().getString("walletAddress");

            jsonViewModel = new JsonViewModel(getContext(), languageKey);

            ImageButton backArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_send_back_arrow);

            TextView sendTextView = (TextView) getView().findViewById(R.id.textView_send_langValue_send);
            sendTextView.setText(jsonViewModel.getSendByLangValues());

            TextView sendNetworkTextView = (TextView) getView().findViewById(R.id.textView_send_langValue_network);
            sendNetworkTextView.setText(jsonViewModel.getNetworkByLangValues());

            TextView sendNetworkValueTextView = (TextView) getView().findViewById(R.id.textView_send_network_value);
            sendNetworkValueTextView.setText( GlobalMethods.BLOCKCHAIN_NAME);

            TextView balanceTextView = (TextView) getView().findViewById(R.id.textView_send_langValue_balance);
            balanceTextView.setText(jsonViewModel.getBalanceByLangValues());

            TextView balanceCoinSymbolTextView = (TextView) getView().findViewById(R.id.textView_send_coin_symbol);
            balanceCoinSymbolTextView.setText(GlobalMethods.COIN_SYMBOL);

            TextView balanceValueTextView = (TextView) getView().findViewById(R.id.textView_send_balance_value);

            ProgressBar progressBar = (ProgressBar)  getView().findViewById(R.id.progress_send_loader);

            TextView addressToSendTextView = (TextView) getView().findViewById(R.id.textView_send_address_to_send);
            addressToSendTextView.setText(jsonViewModel.getAddressToSendByLangValues());

            EditText addressToSendEditText = (EditText) getView().findViewById(R.id.editText_send_address_to_send);
            addressToSendEditText.setHint(jsonViewModel.getAddressToSendByLangValues());

            ImageButton qrCodeImageButton = (ImageButton) getView().findViewById(R.id.imageButton_scan_qr_code);

            TextView quantityToSendTextView = (TextView) getView().findViewById(R.id.textView_send_quantity_to_send);
            quantityToSendTextView.setText(jsonViewModel.getQuantityToSendByLangValues());

            EditText quantityToSendEditText = (EditText) getView().findViewById(R.id.editText_send_quantity_to_send);
            quantityToSendEditText.setHint(jsonViewModel.getQuantityToSendByLangValues());

            Button sendButton = (Button) getView().findViewById(R.id.button_send_send);
            sendButton.setText(jsonViewModel.getSendByLangValues());

            //////////////////////////////////////////////////////////////
            ////addressToSendEditText.setText("0xF0CC7651951D8B08Dac76f9E03f22374935661fB134E402f0943981282f5B047");
            ////quantityToSendEditText.setText("50000");
            //////////////////////////////////////////////////////////////

            ProgressBar progressBarSendCoins = (ProgressBar)  getView().findViewById(R.id.progress_loader_send_coins);

            linerLayoutOffline = (LinearLayout) getView().findViewById(R.id.linerLayout_send_offline);
            imageViewRetry = (ImageView) getView().findViewById(R.id.image_retry);
            textViewTitleRetry = (TextView) getView().findViewById(R.id.textview_title_retry);
            textViewSubTitleRetry = (TextView) getView().findViewById(R.id.textview_subtitle_retry);

            Button buttonRetry = (Button) getView().findViewById(R.id.button_retry);

            TextView assetLabelTextView = (TextView) getView().findViewById(R.id.textView_send_asset_label);
            assetLabelTextView.setText(jsonViewModel.getAssetToSendByLangValues());

            final Spinner assetSpinner = (Spinner) getView().findViewById(R.id.spinner_send_asset);
            final TextView assetSelectedTextView = (TextView) getView().findViewById(R.id.textView_send_asset_selected);

            setupAssetSpinner(assetSpinner, assetSelectedTextView, balanceValueTextView,
                    progressBar, walletAddress);
            refreshTokenOptionsFromApi(assetSpinner, assetSelectedTextView, balanceValueTextView,
                    progressBar, walletAddress);

            backArrowImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mSendListener.onSendComplete(null);
                }
            });

            qrCodeImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    QRCodeDialogFragment(view, addressToSendEditText, languageKey);
                }
            });

            sendButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        if(sendButtonStatus == 1){
                            return;
                        }
                        sendButtonStatus = 1;
                        String message = jsonViewModel.getQuantumAddr();
                        if (addressToSendEditText.getText().toString().startsWith(GlobalMethods.ADDRESS_START_PREFIX)) {
                            if (addressToSendEditText.getText().toString().length() == GlobalMethods.ADDRESS_LENGTH) {
                                if (quantityToSendEditText.getText().toString().length() > 0) {
                                    String toAddress = addressToSendEditText.getText().toString();
                                    String quantity = quantityToSendEditText.getText().toString();

                                    if (progressBarSendCoins.getVisibility() == View.VISIBLE) {
                                        message = getResources().getString(R.string.send_transaction_message_exits);
                                        GlobalMethods.ShowToast(getContext(), message);
                                    } else {
                                        unlockDialogFragment(view, progressBarSendCoins,
                                                walletAddress, toAddress, quantity, languageKey);
                                    }
                                    return;
                                }
                                message = jsonViewModel.getEnterAmount();
                            }
                        }
                        messageDialogFragment(languageKey, message);
                        sendButtonStatus = 0;
                    }catch (Exception e){
                        GlobalMethods.ExceptionError(getContext(), TAG, e);
                    }
                }
            });

            buttonRetry.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v) {
                    switch (retryStatus) {
                        case 0:
                            getBalanceByAccount(walletAddress, balanceTextView, progressBar);
                            break;
                        case 1:
                            String  message = getResources().getString(R.string.send_transaction_message_description);
                            messageDialogFragment(languageKey, message);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    public static interface OnSendCompleteListener {
        public abstract void onSendComplete(String sendPassword);
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mSendListener = (SendFragment.OnSendCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

    private void messageDialogFragment(String languageKey, String message) {
        try {
            Bundle bundleRoute = new Bundle();
            bundleRoute.putString("languageKey", languageKey);
            bundleRoute.putString("message", message);

            FragmentManager fragmentManager  = getFragmentManager();
            MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
            messageDialogFragment.setCancelable(false);
            messageDialogFragment.setArguments(bundleRoute);
            messageDialogFragment.show(fragmentManager, "");
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void unlockDialogFragment(View view, ProgressBar progressBarSendCoins,
                                     String walletAddress, String toAddress, String quantity, String languageKey) {
        try {
            //Alert unlock dialog
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle((CharSequence) "").setView((int)
                            R.layout.unlock_dialog_fragment).create();
            dialog.dismiss();
            dialog.setCancelable(false);
            dialog.show();

            TextView unlockWalletTextView = (TextView) dialog.findViewById(R.id.textView_unlock_langValues_unlock_wallet);
            unlockWalletTextView.setText(jsonViewModel.getUnlockWalletByLangValues());

            TextView unlockPasswordTextView = (TextView) dialog.findViewById(R.id.textView_unlock_langValues_enter_wallet_password);
            unlockPasswordTextView.setText(jsonViewModel.getEnterQuantumWalletPasswordByLangValues());

            EditText passwordEditText = (EditText) dialog.findViewById(R.id.editText_unlock_langValues_enter_a_password);
            passwordEditText.setHint(jsonViewModel.getEnterApasswordByLangValues());

            Button unlockButton = (Button) dialog.findViewById(R.id.button_unlock_langValues_unlock);
            unlockButton.setText(jsonViewModel.getUnlockByLangValues());

            Button closeButton = (Button) dialog.findViewById(R.id.button_unlock_langValues_close);
            closeButton.setText(jsonViewModel.getCloseByLangValues());

            unlockButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String password = passwordEditText.getText().toString();
                    if (password == null || password.isEmpty()) {
                        messageDialogFragment(languageKey, jsonViewModel.getEnterApasswordByLangValues());
                        return;
                    }
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    if (!secureStorage.verifyPassword(getContext(), password.trim())) {
                        messageDialogFragment(languageKey,
                                jsonViewModel.getWalletPasswordMismatchByErrors());
                        return;
                    }
                    if (sendButtonStatus == 1) {
                        dialog.dismiss();
                        sendTransaction(getContext(), progressBarSendCoins,
                                walletAddress, toAddress, quantity, languageKey);
                    }
                    sendButtonStatus = 2;
                }
            });

            closeButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dialog.dismiss();
                    sendButtonStatus = 0;
                }
            });

        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void getBalanceByAccount(String address, TextView balanceTextView, ProgressBar progressBar) {
        try{
            retryStatus = 0;

            linerLayoutOffline.setVisibility(View.GONE);

            //Internet connection check
            if (GlobalMethods.IsNetworkAvailable(getContext())) {

                progressBar.setVisibility(View.VISIBLE);

                String[] taskParams = { address };

                AccountBalanceRestTask task = new AccountBalanceRestTask(
                    getContext(), new AccountBalanceRestTask.TaskListener() {
                    @Override
                    public void onFinished(BalanceResponse balanceResponse) throws ServiceException {
                        if (balanceResponse.getResult().getBalance() != null) {
                            String value = balanceResponse.getResult().getBalance().toString();
                            String quantity = CoinUtils.formatWei(value);
                            balanceTextView.setText(quantity);
                        }
                        progressBar.setVisibility(View.GONE);
                    }
                    @Override
                    public void onFailure(com.quantumcoinwallet.app.api.read.ApiException e) {
                        progressBar.setVisibility(View.GONE);
                        int code = e.getCode();
                        boolean check = GlobalMethods.ApiExceptionSourceCodeBoolean(code);
                        if(check==true) {
                            GlobalMethods.ApiExceptionSourceCodeRoute(getContext(), code,
                                    getString(R.string.apierror),
                                    TAG + " : AccountBalanceRestTask : " + e.toString());
                        } else {
                            GlobalMethods.OfflineOrExceptionError(getContext(),
                                    linerLayoutOffline, imageViewRetry, textViewTitleRetry,
                                    textViewSubTitleRetry, true);
                        }
                    }
                 });

                task.execute(taskParams);
            } else {
                GlobalMethods.OfflineOrExceptionError(getContext(),
                        linerLayoutOffline, imageViewRetry, textViewTitleRetry,
                        textViewSubTitleRetry, false);
            }
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void sendTransaction(Context context, ProgressBar progressBar,
                                 String fromAddress, String toAddress, String quantity, String languageKey) {
        try {
            if (progressBar.getVisibility() == View.VISIBLE) {
                String message = getResources().getString(R.string.send_transaction_message_exits);
                GlobalMethods.ShowToast(getContext(), message);
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            String indexStr = PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.get(fromAddress);
            if (indexStr == null) {
                throw new Exception("Wallet not found for address");
            }
            String walletJsonStr = secureStorage.loadWallet(context, Integer.parseInt(indexStr));
            JSONObject walletData = new JSONObject(walletJsonStr);
            String privKeyBase64 = walletData.getString("privateKey");
            String pubKeyBase64 = walletData.getString("publicKey");

            String rpcEndpoint = GlobalMethods.RPC_ENDPOINT_URL;
            int chainId = Integer.parseInt(GlobalMethods.NETWORK_ID);
            boolean advancedSigningEnabled = PrefConnect.readBoolean(context, PrefConnect.ADVANCED_SIGNING_ENABLED_KEY, false);

            BridgeCallback callback = new BridgeCallback() {
                @Override
                public void onResult(String jsonResult) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                JSONObject result = new JSONObject(jsonResult);
                                JSONObject data = result.getJSONObject("data");
                                String txHash = data.getString("txHash");
                                sendCompletedDialogFragment(context);
                            } catch (Exception e) {
                                progressBar.setVisibility(View.GONE);
                                sendButtonStatus = 0;
                                String errorTitle = jsonViewModel.getErrorTitleByLangValues();
                                String prefix = jsonViewModel.getErrorOccurredByLangValues();
                                GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                        prefix + sanitizeErrorMessage(e.getMessage()));
                            }
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            sendButtonStatus = 0;
                            String errorTitle = jsonViewModel.getErrorTitleByLangValues();
                            String prefix = jsonViewModel.getErrorOccurredByLangValues();
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    prefix + sanitizeErrorMessage(error));
                        }
                    });
                }
            };

            if (selectedContract == null) {
                String valueWei = CoinUtils.parseEther(quantity);
                String gasLimit = GlobalMethods.GAS_QCN_LIMIT;
                keyViewModel.sendTransaction(privKeyBase64, pubKeyBase64, toAddress, valueWei,
                        gasLimit, rpcEndpoint, chainId, advancedSigningEnabled, callback);
            } else {
                String amountWei = CoinUtils.parseUnits(quantity, selectedDecimals);
                String gasLimit = GlobalMethods.GAS_TOKEN_LIMIT;
                keyViewModel.sendTokenTransaction(privKeyBase64, pubKeyBase64, selectedContract,
                        toAddress, amountWei, gasLimit, rpcEndpoint, chainId,
                        advancedSigningEnabled, callback);
            }
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            sendButtonStatus = 0;
            String errorTitle = jsonViewModel.getErrorTitleByLangValues();
            String prefix = jsonViewModel.getErrorOccurredByLangValues();
            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                    prefix + sanitizeErrorMessage(e.getMessage()));
        }
    }

    /**
     * Builds the asset spinner with "Q" first and one entry per cached token.
     * Selection mutates `selectedContract/Symbol/Decimals/TokenBalanceWei` and
     * updates the displayed balance.
     */
    private void setupAssetSpinner(final Spinner spinner,
                                   final TextView assetSelectedTextView,
                                   final TextView balanceValueTextView,
                                   final ProgressBar balanceProgress,
                                   final String walletAddress) {
        tokenOptions = new ArrayList<>();
        if (GlobalMethods.CURRENT_WALLET_TOKEN_LIST != null
                && Objects.equals(GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS, walletAddress)) {
            tokenOptions.addAll(GlobalMethods.CURRENT_WALLET_TOKEN_LIST);
        }

        List<String> labels = new ArrayList<>();
        labels.add("Q");
        for (AccountTokenSummary t : tokenOptions) {
            labels.add(formatSpinnerLabel(t));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedContract = null;
                    selectedSymbol = "Q";
                    selectedDecimals = 18;
                    selectedTokenBalanceWei = null;
                    assetSelectedTextView.setText("QuantumCoin");
                    getBalanceByAccount(walletAddress, balanceValueTextView, balanceProgress);
                    return;
                }
                int tokenIndex = position - 1;
                if (tokenIndex < 0 || tokenIndex >= tokenOptions.size()) {
                    return;
                }
                AccountTokenSummary token = tokenOptions.get(tokenIndex);
                selectedContract = token.getContractAddress();
                selectedSymbol = token.getSymbol();
                selectedDecimals = token.getDecimals() == null ? 18 : token.getDecimals();
                selectedTokenBalanceWei = token.getTokenBalance();
                assetSelectedTextView.setText(safe(token.getContractAddress()));
                balanceValueTextView.setText(
                        CoinUtils.formatUnits(safe(selectedTokenBalanceWei), selectedDecimals));
                balanceProgress.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * Kicks a best-effort refresh of the token list from the scan API so the spinner
     * reflects the latest set of holdings. Runs silently when offline or on failure.
     */
    private void refreshTokenOptionsFromApi(final Spinner spinner,
                                            final TextView assetSelectedTextView,
                                            final TextView balanceValueTextView,
                                            final ProgressBar balanceProgress,
                                            final String walletAddress) {
        if (walletAddress == null || walletAddress.isEmpty()) return;
        if (!GlobalMethods.IsNetworkAvailable(getContext())) return;
        try {
            String[] params = { walletAddress, "1" };
            ListAccountTokensRestTask task = new ListAccountTokensRestTask(
                    getContext(), new ListAccountTokensRestTask.TaskListener() {
                @Override
                public void onFinished(AccountTokenListResponse response) {
                    List<AccountTokenSummary> items = (response == null || response.getItems() == null)
                            ? new ArrayList<AccountTokenSummary>()
                            : response.getItems();
                    GlobalMethods.CURRENT_WALLET_TOKEN_LIST = new ArrayList<>(items);
                    GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS = walletAddress;
                    setupAssetSpinner(spinner, assetSelectedTextView, balanceValueTextView,
                            balanceProgress, walletAddress);
                }

                @Override
                public void onFailure(ApiException apiException) {
                    // silent: spinner keeps the existing options
                }
            });
            task.execute(params);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private static String formatSpinnerLabel(AccountTokenSummary token) {
        String symbol = token.getSymbol() == null ? "" : token.getSymbol();
        String name = token.getName() == null ? "" : token.getName();
        if (name.isEmpty()) return symbol;
        return symbol + " (" + name + ")";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) return "";
        return android.text.Html.fromHtml(message, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replaceAll("[<>]", "");
    }

    private void sendCompletedDialogFragment(Context context) {
        try {
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle((CharSequence) "").setView((int)
                            R.layout.send_completed_dialog_fragment).create();
            dialog.setCancelable(false);
            dialog.show();
            TextView textViewOk = (TextView) dialog.findViewById(
                    R.id.textView_send_completed_alert_dialog_ok);
            textViewOk.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    sendButtonStatus = 0;
                    dialog.dismiss();
                    mSendListener.onSendComplete(null);
                }
            });
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }


    private void QRCodeDialogFragment(View view, EditText walletAddressEditText, String languageKey) {
        try {
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle((CharSequence) "").setView((int)
                            R.layout.qrcode_dialog_fragment).create();

            dialog.dismiss();
            dialog.setCancelable(false);
            dialog.show();

            SurfaceView qrCodeSurfaceView = dialog.findViewById(R.id.surfaceView_qrcode);
            TextView qrcodeTextView = dialog.findViewById(R.id.textView_qrcode);

            Button okButton = (Button) dialog.findViewById(R.id.button_qrcode_langValues_ok);
            Button closeButton = (Button) dialog.findViewById(R.id.button_qrcode_langValues_close);

            okButton.setText(jsonViewModel.getOkByLangValues());
            closeButton.setText(jsonViewModel.getCloseByLangValues());

            barcodeDetector = new BarcodeDetector.Builder(getContext())
                    .setBarcodeFormats(Barcode.ALL_FORMATS)
                    .build();

            cameraSource = new CameraSource.Builder(getContext(), barcodeDetector)
                    .setRequestedPreviewSize(1920, 1080)
                    .setAutoFocusEnabled(true)
                    .build();

            qrCodeSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    try {
                        if (ActivityCompat.checkSelfPermission(getActivity().getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraSource.start(qrCodeSurfaceView.getHolder());
                        } else {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA))
                            {
                                qrcodeTextView.setText(R.string.send_camara_permission_description);
                                Toast.makeText(getActivity(), R.string.send_camara_permission_description, Toast.LENGTH_SHORT).show();
                            } else {
                                ActivityCompat.requestPermissions(getActivity(), new
                                        String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                                dialog.dismiss();
                                dialog.setCancelable(false);
                                dialog.show();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    cameraSource.stop();
                }
            });


            barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
                @Override
                public void release() {
                    //Toast.makeText(getActivity(), "To prevent memory leaks barcode scanner has been stopped", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                    final SparseArray<Barcode> barCode = detections.getDetectedItems();
                    if (barCode.size() > 0) {
                        qrcodeTextView.post(new Runnable() {
                            @Override
                            public void run() {
                                String intentData = barCode.valueAt(0).displayValue;
                                qrcodeTextView.setText(intentData);
                            }
                        });
                    }
                }
            });

            okButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cameraSource.stop();
                    if (qrcodeTextView.getText().toString().startsWith(GlobalMethods.ADDRESS_START_PREFIX)) {
                        walletAddressEditText.setText(qrcodeTextView.getText());
                    } else {
                        walletAddressEditText.setText("");
                    }
                    v.post(new Runnable() {
                        public void run() {
                            cameraSource.release();
                            dialog.dismiss();
                        }
                    });
                }
            });

            closeButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cameraSource.stop();
                    v.post(new Runnable() {
                        public void run() {
                            cameraSource.release();
                            dialog.dismiss();
                        }
                    });
                }
            });

        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

}
