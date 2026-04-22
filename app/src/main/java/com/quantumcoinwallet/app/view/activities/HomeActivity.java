package com.quantumcoinwallet.app.view.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.ApiClient;
import com.quantumcoinwallet.app.api.read.model.BalanceResponse;
import com.quantumcoinwallet.app.asynctask.read.AccountBalanceRestTask;
import com.quantumcoinwallet.app.entity.ServiceException;
import com.quantumcoinwallet.app.model.BlockchainNetwork;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.utils.Utility;
import com.quantumcoinwallet.app.view.fragment.BlockchainNetworkAddFragment;
import com.quantumcoinwallet.app.view.fragment.BlockchainNetworkDialogFragment;
import com.quantumcoinwallet.app.view.fragment.BlockchainNetworkFragment;
import com.quantumcoinwallet.app.view.fragment.HomeMainFragment;
import com.quantumcoinwallet.app.view.fragment.HomeStartFragment;
import com.quantumcoinwallet.app.view.fragment.HomeWalletFragment;
import com.quantumcoinwallet.app.view.fragment.ReceiveFragment;
import com.quantumcoinwallet.app.view.fragment.SendFragment;
import com.quantumcoinwallet.app.view.fragment.SettingsFragment;
import com.quantumcoinwallet.app.view.fragment.RevealWalletFragment;
import com.quantumcoinwallet.app.view.fragment.AccountTransactionsFragment;

import com.quantumcoinwallet.app.view.fragment.WalletsFragment;
import com.quantumcoinwallet.app.utils.CoinUtils;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HomeActivity extends FragmentActivity implements
        HomeMainFragment.OnHomeMainCompleteListener, HomeStartFragment.OnHomeStartCompleteListener,
        HomeWalletFragment.OnHomeWalletCompleteListener,
        BlockchainNetworkDialogFragment.OnBlockchainNetworkDialogCompleteListener,
        BlockchainNetworkFragment.OnBlockchainNetworkCompleteListener,
        BlockchainNetworkAddFragment.OnBlockchainNetworkAddCompleteListener,
        SendFragment.OnSendCompleteListener, ReceiveFragment.OnReceiveCompleteListener,
        AccountTransactionsFragment.OnAccountTransactionsCompleteListener, WalletsFragment.OnWalletsCompleteListener,
        SettingsFragment.OnSettingsCompleteListener,
        RevealWalletFragment.OnRevealWalletCompleteListener {

    private static final String TAG = "HomeActivity";
    private static final long UNLOCK_TIMEOUT_MS = 300_000;

    private final int notificationRequestCode = 112;
    private long lastUnlockTimestamp = 0L;
    private boolean unlockDialogShowing = false;
    private boolean suppressNextResumeLock = false;

    // L-09: in-foreground idle-lock. The onResume elapsed-time check is
    // second line of defence; the timer runs continuously while the
    // Activity is resumed and fires lock + unlock prompt at UNLOCK_TIMEOUT_MS
    // of user-interaction inactivity.
    private final Handler idleLockHandler = new Handler(android.os.Looper.getMainLooper());
    private final Runnable idleLockRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                if (secureStorage != null
                        && secureStorage.isInitialized(getApplicationContext())
                        && secureStorage.isUnlocked()
                        && !unlockDialogShowing) {
                    secureStorage.lock();
                    showUnlockDialog(null);
                }
            } catch (Throwable ignore) { }
        }
    };

    private void resetIdleLockTimer() {
        idleLockHandler.removeCallbacks(idleLockRunnable);
        idleLockHandler.postDelayed(idleLockRunnable, UNLOCK_TIMEOUT_MS);
    }

    private LinearLayout topLinearLayout;
    private ViewGroup.LayoutParams topLinearLayoutParams;
    private RelativeLayout centerRelativeLayout;

    private Bundle bundle;

    private String walletAddress = "";

    private TextView blockChainNetworkTextView;

    private TextView walletAddressTextView;
    private TextView balanceValueTextView;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNavigationView;

    private LinearLayout linerLayoutOffline;
    private ImageView imageViewRetry;
    private TextView textViewTitleRetry;
    private TextView textViewSubTitleRetry;

    private JsonViewModel jsonViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        try {
            //locale language

            //String languageKey = getIntent().getStringExtra("languageKey");
            String languageKey="en";

            //Bundle
            bundle = new Bundle();
            bundle.putString("languageKey", languageKey);

            jsonViewModel = new JsonViewModel(getApplicationContext(),languageKey);

            setContentView(R.layout.home_activity);

            KeyViewModel.initBridge(getApplicationContext());

            // loadSeedsThread depends on KeyViewModel.getBridge() being non-null,
            // so it must run AFTER initBridge(). It also internally calls
            // initializeOffline() before getAllSeedWords(), so no separate
            // startup init thread is needed.
            loadSeedsThread();

            //Linear top layout
            topLinearLayout = (LinearLayout) findViewById(R.id.top_linear_layout_home_id);
            topLinearLayoutParams = topLinearLayout.getLayoutParams();
            blockChainNetworkTextView = (TextView) findViewById(R.id.textView_home_blockchain_network);
            TextView titleTextView = (TextView) findViewById(R.id.textView_home_tile);
            TextView loadSeedTextView = (TextView) findViewById(R.id.textView_home_load_seed);

            //loadSeedsThread(loadSeedTextView);

            //Center Relative layout & Image Button
            centerRelativeLayout = (RelativeLayout) findViewById(R.id.center_relative_layout_home_id);
            walletAddressTextView = (TextView) findViewById(R.id.textView_home_wallet_address);
            ImageButton copyClipboardImageButton = (ImageButton) findViewById(R.id.imageButton_home_copy_clipboard);
            TextView homeCopiedTextView = (TextView) findViewById(R.id.textView_home_copied);
            homeCopiedTextView.setText(jsonViewModel.getCopiedByLangValues());
            ImageButton blockExploreImageButton = (ImageButton) findViewById(R.id.imageButton_home_block_explore);

            TextView balanceCoinSymbolTextView = (TextView) findViewById(R.id.textView_home_coin_symbol);
            balanceValueTextView = (TextView) findViewById(R.id.textView_home_balance_value);
            ImageButton refreshImageButton = (ImageButton) findViewById(R.id.imageButton_home_refresh);
            progressBar = (ProgressBar) findViewById(R.id.progress_home_loader);

            TextView sendTitleTextView = (TextView) findViewById(R.id.textView_home_send_title);
            ImageButton sendImageButton = (ImageButton) findViewById(R.id.imageButton_home_send);
            TextView receiveTitleTextView = (TextView) findViewById(R.id.textView_home_receive_title);
            ImageButton receiveImageButton = (ImageButton) findViewById(R.id.imageButton_home_receive);
            TextView transactionsTitleTextView = (TextView) findViewById(R.id.textView_home_transactions_title);
            ImageButton transactionsImageButton = (ImageButton) findViewById(R.id.imageButton_home_transactions);

            //Bottom navigation
            bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);

            linerLayoutOffline = (LinearLayout) findViewById(R.id.linerLayout_home_offline);
            imageViewRetry = (ImageView) findViewById(R.id.image_retry);
            textViewTitleRetry = (TextView) findViewById(R.id.textview_title_retry);
            textViewSubTitleRetry = (TextView) findViewById(R.id.textview_subtitle_retry);
            Button buttonRetry = (Button) findViewById(R.id.button_retry);

            titleTextView.setText(jsonViewModel.getTitleByLangValues());
            //balanceTitleTextView.setText(jsonViewModel.getBalanceByLangValues());
            balanceCoinSymbolTextView.setText(GlobalMethods.COIN_SYMBOL);

            sendTitleTextView.setText(jsonViewModel.getSendByLangValues());
            receiveTitleTextView.setText(jsonViewModel.getReceiveByLangValues());
            transactionsTitleTextView.setText(jsonViewModel.getTransactionsByLangValues());

            walletAddressTextView.setText("");
            balanceValueTextView.setText("");
            screenViewType(-1);

            //Notification permission
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, notificationRequestCode);
                }
                createNotificationChannel();
            }

            int storedNetworkIndex = PrefConnect.readInteger(getApplicationContext(),
                    PrefConnect.BLOCKCHAIN_NETWORK_ID_INDEX_KEY, 0);

            List<BlockchainNetwork> blockchainNetworkList = GlobalMethods.BlockChainNetworkRead(getApplicationContext());
            int maxNetworkIdx = Math.max(0, blockchainNetworkList.size() - 1);
            final int blockchainNetworkIdIndex = Math.max(0, Math.min(storedNetworkIndex, maxNetworkIdx));
            if (blockchainNetworkIdIndex != storedNetworkIndex) {
                android.util.Log.w(TAG, "Stored network index out of range; clamped to " + blockchainNetworkIdIndex);
                PrefConnect.writeInteger(getApplicationContext(),
                        PrefConnect.BLOCKCHAIN_NETWORK_ID_INDEX_KEY, blockchainNetworkIdIndex);
            }
            BlockchainNetwork blockchainNetwork = blockchainNetworkList.get(blockchainNetworkIdIndex);
            GlobalMethods.setActiveNetwork(blockchainNetwork);

            blockChainNetworkTextView.setText(GlobalMethods.BLOCKCHAIN_NAME);

            blockChainNetworkTextView.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    bundle.putString("blockchainNetworkIdIndex", String.valueOf(blockchainNetworkIdIndex));

                    BlockchainNetworkDialogFragment blockChainDialogFragment = BlockchainNetworkDialogFragment.newInstance();
                    blockChainDialogFragment.setCancelable(false);
                    blockChainDialogFragment.setArguments(bundle);
                    blockChainDialogFragment.show(getSupportFragmentManager(), "BlockchainNetworkDialog");
                }
            });

            //Center buttons setOnClickListener
            copyClipboardImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // L-05: IS_SENSITIVE so Android 13+ clipboard previews
                    // do not render the full address.
                    com.quantumcoinwallet.app.utils.SecureClipboard.copyAddress(
                            HomeActivity.this, "currentAddress",
                            walletAddressTextView.getText());
                    homeCopiedTextView.setVisibility(View.VISIBLE);
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            homeCopiedTextView.setVisibility(View.GONE);
                        }
                    }, 600);
                }
            });

            blockExploreImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(GlobalMethods.BLOCK_EXPLORER_URL + GlobalMethods.BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL.replace("{address}", walletAddressTextView.getText())))
                    );
                }
            });

            refreshImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    getBalanceByAccount(walletAddress, balanceValueTextView, progressBar);
                }
            });

            sendImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    beginTransactionNow(SendFragment.newInstance(), bundle);
                    screenViewType(1);
                }
            });

            receiveImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    beginTransactionNow(ReceiveFragment.newInstance(), bundle);
                    screenViewType(1);
                }
            });

            transactionsImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    beginTransactionNow(AccountTransactionsFragment.newInstance(), bundle);
                    screenViewType(1);
                }
            });

            buttonRetry.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                }
            });

            bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.nav_wallets) {
                        if (walletAddress.startsWith(GlobalMethods.ADDRESS_START_PREFIX)) {
                            if (walletAddress.length() == GlobalMethods.ADDRESS_LENGTH){
                                screenViewType(1);
                                beginTransactionNow(WalletsFragment.newInstance(), bundle);
                            }
                        }
                        return true;
                    } else if (id == R.id.nav_help) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(GlobalMethods.DP_DOCS_URL))
                        );
                        return true;
                    } else if (id == R.id.nav_block_explorer) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(GlobalMethods.BLOCK_EXPLORER_URL))
                        );
                        return true;
                    } else if (id == R.id.nav_settings) {
                        screenViewType(1);
                        beginTransactionNow(SettingsFragment.newInstance(), bundle);
                        return true;
                    }
                    return false;
                }
            });

            deselectAllNavItems();

            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            boolean secureInitialized = secureStorage != null && secureStorage.isInitialized(getApplicationContext());

            if (secureInitialized) {
                showUnlockDialog(new Runnable() {
                    @Override
                    public void run() {
                        walletAddressTextView.setText(walletAddress);
                        screenViewType(0);
                        beginTransaction(HomeMainFragment.newInstance(), bundle);
                        notificationThread(1);
                    }
                });
            } else {
                screenViewType(-1);
                beginTransaction(HomeStartFragment.newInstance(), bundle);
            }

        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onBackPressed() {
        if (unlockDialogShowing) {
            return;
        }
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.frame_home_container_id);
        if (current instanceof SettingsFragment
                || current instanceof ReceiveFragment
                || current instanceof SendFragment
                || current instanceof AccountTransactionsFragment
                || current instanceof RevealWalletFragment
                || current instanceof BlockchainNetworkFragment
                || current instanceof BlockchainNetworkAddFragment
                || current instanceof WalletsFragment) {
            screenViewType(0);
            beginTransactionNow(HomeMainFragment.newInstance(), bundle);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onHomeMainComplete() {
        try {
            getBalanceByAccount(walletAddress, balanceValueTextView, progressBar);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onHomeStartComplete() {
        try {
            screenViewType(-1);
            beginTransaction(HomeWalletFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onHomeWalletCompleteByHomeMain( String indexKey) {
        try {
            getCurrentWallet(indexKey);
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
            notificationThread(1);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onHomeWalletCompleteByWallets() {
        try {
            screenViewType(1);
            beginTransaction(WalletsFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onBlockchainNetworkDialogCancel() {
        try{
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @SuppressLint("UnsafeIntentLaunch")
    @Override
    public void onBlockchainNetworkDialogOk() {
        try{
            // Network switch invalidates the scan-API token cache.
            GlobalMethods.CURRENT_WALLET_TOKEN_LIST = new java.util.ArrayList<>();
            GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS = null;
            finish();
            startActivity(getIntent());
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onBlockchainNetworkCompleteByBackArrow() {
        try{
            screenViewType(1);
            beginTransaction(SettingsFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onBlockchainNetworkCompleteByAdd() {
        try{
            screenViewType(1);
            beginTransaction(BlockchainNetworkAddFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onBlockchainNetworkAddComplete() {
        try{
            screenViewType(1);
            beginTransaction(BlockchainNetworkFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onSendComplete(String password) {
        try {
            bundle.remove("sendPassword");
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onReceiveComplete() {
        try {
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onAccountTransactionsComplete() {
        try {
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void  onWalletsCompleteByBackArrow(){
        screenViewType(0);
        beginTransactionNow(HomeMainFragment.newInstance(), bundle);
    }

    @Override
    public void  onWalletsCompleteByCreateOrRestore(){
        screenViewType(1);
        beginTransactionNow(HomeWalletFragment.newInstance(), bundle);
    }

    @Override
    public void  onWalletsCompleteBySwitchAddress(String indexKey){
        getCurrentWallet(indexKey);
        screenViewType(0);
        beginTransaction(HomeMainFragment.newInstance(), bundle);
    }

    @Override
    public void onWalletsCompleteByReveal(String walletAddress){
        screenViewType(1);
        bundle.putString("walletAddress", walletAddress);
        beginTransaction(RevealWalletFragment.newInstance(), bundle);
    }

    @Override
    public void onSettingsCompleteCompleteByBackArrow() {
        try {
            screenViewType(0);
            beginTransaction(HomeMainFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onSettingsCompleteByNetwork() {
        try {
            screenViewType(1);
            beginTransaction(BlockchainNetworkFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    public void onRevealWalletComplete() {
        try {
            screenViewType(1);
            beginTransaction(WalletsFragment.newInstance(), bundle);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (suppressNextResumeLock) {
                suppressNextResumeLock = false;
                lastUnlockTimestamp = android.os.SystemClock.elapsedRealtime();
                resetIdleLockTimer();
                return;
            }
            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            long now = android.os.SystemClock.elapsedRealtime();
            long elapsed = now - lastUnlockTimestamp;
            boolean nonMonotonic = elapsed < 0;
            if (secureStorage != null
                    && secureStorage.isInitialized(getApplicationContext())
                    && !unlockDialogShowing
                    && (nonMonotonic || elapsed > UNLOCK_TIMEOUT_MS)) {
                secureStorage.lock();
                showUnlockDialog(null);
            } else {
                // L-09: (re)arm the in-foreground idle-lock timer on every resume.
                resetIdleLockTimer();
            }
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    @Override
    protected void onPause() {
        // L-09: stop the timer while we are not foregrounded; onResume will
        // re-evaluate staleness via the elapsed-time check.
        idleLockHandler.removeCallbacks(idleLockRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        idleLockHandler.removeCallbacks(idleLockRunnable);
        super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // L-09: any tap/swipe/key press resets the idle-lock countdown.
        resetIdleLockTimer();
    }

    public long markUnlockedNow() {
        lastUnlockTimestamp = android.os.SystemClock.elapsedRealtime();
        resetIdleLockTimer();
        return lastUnlockTimestamp;
    }

    public void setSuppressNextResumeLock(boolean suppress) {
        this.suppressNextResumeLock = suppress;
    }

    private void showUnlockDialog(final Runnable onSuccess) {
        showUnlockDialog(onSuccess, false);
    }

    private void showUnlockDialog(final Runnable onSuccess, final boolean forceModal) {
        if (unlockDialogShowing) return;
        unlockDialogShowing = true;

        try {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle((CharSequence) "")
                    .setView((int) R.layout.unlock_dialog_fragment)
                    .create();
            dialog.setCancelable(false);
            if (forceModal) {
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(android.content.DialogInterface d, int keyCode,
                                         android.view.KeyEvent event) {
                        return keyCode == android.view.KeyEvent.KEYCODE_BACK;
                    }
                });
            }
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(
                                android.graphics.Color.TRANSPARENT));
            }
            dialog.show();

            TextView unlockWalletTextView = (TextView) dialog.findViewById(
                    R.id.textView_unlock_langValues_unlock_wallet);
            unlockWalletTextView.setText(jsonViewModel.getUnlockWalletByLangValues());

            TextView unlockPasswordTextView = (TextView) dialog.findViewById(
                    R.id.textView_unlock_langValues_enter_wallet_password);
            unlockPasswordTextView.setText(jsonViewModel.getEnterQuantumWalletPasswordByLangValues());

            android.widget.EditText passwordEditText = (android.widget.EditText) dialog.findViewById(
                    R.id.editText_unlock_langValues_enter_a_password);
            passwordEditText.setHint(jsonViewModel.getEnterApasswordByLangValues());
            GlobalMethods.focusAndShowKeyboard(passwordEditText, dialog);

            Button unlockButton = (Button) dialog.findViewById(
                    R.id.button_unlock_langValues_unlock);
            unlockButton.setText(jsonViewModel.getUnlockByLangValues());

            Button closeButton = (Button) dialog.findViewById(
                    R.id.button_unlock_langValues_close);
            closeButton.setText(jsonViewModel.getCloseByLangValues());
            closeButton.setVisibility(View.GONE);

            unlockButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String password = passwordEditText.getText().toString();
                    if (password == null || password.isEmpty()) {
                        GlobalMethods.ShowErrorDialog(HomeActivity.this,
                                jsonViewModel.getErrorTitleByLangValues(),
                                jsonViewModel.getEnterApasswordByLangValues());
                        return;
                    }
                    unlockButton.setEnabled(false);
                    passwordEditText.setEnabled(false);
                    unlockButton.setText("...");

                    final String trimmedPassword = password.trim();
                    final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                            .show(HomeActivity.this, jsonViewModel.getWaitUnlockByLangValues());
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                                boolean unlocked = secureStorage.unlock(
                                        getApplicationContext(), trimmedPassword);
                                if (!unlocked) {
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                            unlockButton.setEnabled(true);
                                            passwordEditText.setEnabled(true);
                                            unlockButton.setText(jsonViewModel.getUnlockByLangValues());
                                            GlobalMethods.ShowErrorDialog(HomeActivity.this,
                                                    jsonViewModel.getErrorTitleByLangValues(),
                                                    jsonViewModel.getWalletPasswordMismatchByErrors());
                                        }
                                    });
                                    return;
                                }
                                Map<String, String>[] maps = secureStorage.buildWalletMaps(
                                        getApplicationContext());
                                PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP = maps[0];
                                PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP = maps[1];
                                PrefConnect.WALLET_CURRENT_ADDRESS_INDEX_VALUE =
                                        PrefConnect.readString(getApplicationContext(),
                                                PrefConnect.WALLET_CURRENT_ADDRESS_INDEX_KEY, "0");
                                loadWalletHasSeedMap();

                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                        getCurrentWallet(PrefConnect.WALLET_CURRENT_ADDRESS_INDEX_VALUE);
                                        lastUnlockTimestamp = android.os.SystemClock.elapsedRealtime();
                                        unlockDialogShowing = false;
                                        dialog.dismiss();
                                        if (onSuccess != null) {
                                            onSuccess.run();
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                        unlockButton.setEnabled(true);
                                        passwordEditText.setEnabled(true);
                                        unlockButton.setText(jsonViewModel.getUnlockByLangValues());
                                        GlobalMethods.ShowErrorDialog(HomeActivity.this,
                                                jsonViewModel.getErrorTitleByLangValues(),
                                                jsonViewModel.getWalletPasswordMismatchByErrors());
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });
        } catch (Exception e) {
            unlockDialogShowing = false;
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    public void requirePasswordReentryThenNavigate(final Runnable onSuccess) {
        try {
            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            if (secureStorage != null && secureStorage.isInitialized(getApplicationContext())) {
                try { secureStorage.lock(); } catch (Exception ignore) { }
            }
        } catch (Exception ignore) { }
        showUnlockDialog(new Runnable() {
            @Override
            public void run() {
                markUnlockedNow();
                if (onSuccess != null) onSuccess.run();
            }
        }, true);
    }

    private void loadWalletHasSeedMap() {
        PrefConnect.WALLET_INDEX_HAS_SEED_MAP.clear();
        for (String indexKey : PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.keySet()) {
            boolean hasSeed = PrefConnect.readBoolean(getApplicationContext(),
                    PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + indexKey, true);
            PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(indexKey, hasSeed);
        }
    }

    private void beginTransaction(Fragment fragment, Bundle bundle) {
        try {
            linerLayoutOffline.setVisibility(View.GONE);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            fragment.setArguments(bundle);
            transaction.replace(R.id.frame_home_container_id, fragment);
            transaction.commit();
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    private void beginTransactionNow(Fragment fragment, Bundle bundle) {
        try {
            linerLayoutOffline.setVisibility(View.GONE);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            fragment.setArguments(bundle);
            transaction.replace(R.id.frame_home_container_id, fragment);
            transaction.commitNow();
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    /**
     * Public helper for fragments to forcibly lock SecureStorage and re-show the global
     * unlock dialog. Used when a wallet-password re-prompt (reveal / backup / send) fails;
     * callers rely on this to hand control back to the canonical unlock flow.
     */
    public void forceUnlockPrompt() {
        try {
            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            if (secureStorage != null) {
                try { secureStorage.lock(); } catch (Exception ignore) { }
            }
        } catch (Exception ignore) { }
        showUnlockDialog(null);
    }

    private void screenViewType(int status) {
        try {
            // 0 - default main screen, 1 - fragment screen, -1 - Start app (default)

            int screenHeight = 0;

            switch (status) {
                case 0:
                    topLinearLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    topLinearLayout.setLayoutParams(topLinearLayoutParams);
                    bottomNavigationView.setVisibility(View.VISIBLE);
                    centerRelativeLayout.setVisibility(View.VISIBLE);
                    blockChainNetworkTextView.setVisibility(View.VISIBLE);
                    deselectAllNavItems();
                    break;
                case 1:
                    screenHeight = (Utility.calculateScreenWidthDp(getApplicationContext()) * 30 / 100);
                    topLinearLayoutParams.height = screenHeight;
                    topLinearLayout.setLayoutParams(topLinearLayoutParams);
                    bottomNavigationView.setVisibility(View.VISIBLE);
                    centerRelativeLayout.setVisibility(View.GONE);
                    blockChainNetworkTextView.setVisibility(View.GONE);
                    break;
                default:
                    screenHeight = (Utility.calculateScreenWidthDp(getApplicationContext()) * 30 / 100);
                    topLinearLayoutParams.height = screenHeight;
                    topLinearLayout.setLayoutParams(topLinearLayoutParams);
                    bottomNavigationView.setVisibility(View.GONE);
                    centerRelativeLayout.setVisibility(View.GONE);
                    blockChainNetworkTextView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    private void deselectAllNavItems() {
        bottomNavigationView.getMenu().setGroupCheckable(0, true, false);
        for (int i = 0; i < bottomNavigationView.getMenu().size(); i++) {
            bottomNavigationView.getMenu().getItem(i).setChecked(false);
        }
        bottomNavigationView.getMenu().setGroupCheckable(0, true, true);
    }

    private void getCurrentWallet(String indexKey) {
        String previousAddress = walletAddress;
        if(PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null) {
            for (Map.Entry<String, String> entry : PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.entrySet()) {
                if (Objects.equals(indexKey, entry.getKey())) {
                    PrefConnect.writeString(getApplicationContext(), PrefConnect.WALLET_CURRENT_ADDRESS_INDEX_KEY, indexKey);
                    walletAddress = entry.getValue();
                    break;
                }
            }
        }
        // Invalidate token cache whenever the active wallet changed.
        if (!Objects.equals(previousAddress, walletAddress)) {
            GlobalMethods.CURRENT_WALLET_TOKEN_LIST = new java.util.ArrayList<>();
            GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS = null;
        }
        bundle.putString("walletAddress", walletAddress);
        walletAddressTextView.setText(walletAddress);
    }

    /**
     * The offline/retry strip ({@code linerLayout_home_offline}) is a sibling of
     * {@code frame_home_container_id} and lives outside the fragment container, so
     * an in-flight balance refresh can surface it even when the user is on a
     * wallet-flow / settings / transactions screen. Only show it when the
     * currently attached fragment is {@link HomeMainFragment} (or before any
     * fragment is attached, to preserve existing cold-start behavior).
     */
    private boolean shouldShowHomeOfflineOverlay() {
        try {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.frame_home_container_id);
            if (current == null) {
                return true;
            }
            return current instanceof HomeMainFragment;
        } catch (Exception e) {
            return true;
        }
    }

    //Get balance task
    private void getBalanceByAccount(String address, TextView balanceValueTextView, ProgressBar progressBar) {
        try {
            linerLayoutOffline.setVisibility(View.GONE);

            //Internet connection check
            if (GlobalMethods.IsNetworkAvailable(getApplicationContext())) {

                progressBar.setVisibility(View.VISIBLE);

                balanceValueTextView.setText("0");

                String[] taskParams = {address};

                AccountBalanceRestTask task = new AccountBalanceRestTask(
                        getApplicationContext(), new AccountBalanceRestTask.TaskListener() {
                    @Override
                    public void onFinished(BalanceResponse balanceResponse) throws ServiceException {
                        if (balanceResponse.getResult().getBalance() != null) {
                            String value = balanceResponse.getResult().getBalance().toString();
                            String quantity = CoinUtils.formatWei(value);

                            balanceValueTextView.setText(quantity);
                        }
                        progressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(com.quantumcoinwallet.app.api.read.ApiException e) {
                        progressBar.setVisibility(View.GONE);

                        int code = e.getCode();
                        boolean check = GlobalMethods.ApiExceptionSourceCodeBoolean(code);
                        if (check == true) {
                            GlobalMethods.ApiExceptionSourceCodeRoute(getApplicationContext(), code,
                                    getString(R.string.apierror),
                                    TAG + " : AccountBalanceRestTask : " + e.toString());
                        } else if (shouldShowHomeOfflineOverlay()) {
                            GlobalMethods.OfflineOrExceptionError(getApplicationContext(),
                                    linerLayoutOffline, imageViewRetry, textViewTitleRetry,
                                    textViewSubTitleRetry, true);
                        }
                    }
                });
                task.execute(taskParams);
            } else if (shouldShowHomeOfflineOverlay()) {
                GlobalMethods.OfflineOrExceptionError(getApplicationContext(),
                        linerLayoutOffline, imageViewRetry, textViewTitleRetry,
                        textViewSubTitleRetry, false);
            }
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getApplicationContext(), TAG, e);
        }
    }

    //Notification
    private void notificationThread(int accountStatus) {
        try {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    //boolean threadStop = false;
                    final int TIME_SLEEP = 5000;
                    final String[] previewsQuantity = new String[1];
                    String[] currentQuantity = new String[1];

                    //New account
                    if (accountStatus == 0) {
                        previewsQuantity[0] = "0";
                    }

                    try {
                        while (true) {

                            if (walletAddress.length() != GlobalMethods.ADDRESS_LENGTH) {
                                break;
                            }

                            String[] taskParams = {walletAddress};

                            AccountBalanceRestTask task = new AccountBalanceRestTask(
                                    getApplicationContext(), new AccountBalanceRestTask.TaskListener() {
                                @Override
                                public void onFinished(BalanceResponse balanceResponse) throws ServiceException {
                                    if (balanceResponse.getResult().getBalance() != null) {
                                        String value = balanceResponse.getResult().getBalance().toString();
                                        currentQuantity[0] = CoinUtils.formatWei(value);
                                        if (previewsQuantity[0] != null) {
                                            if (!previewsQuantity[0].equals(currentQuantity[0])) {
                                                balanceValueTextView.setText(currentQuantity[0]);
                                                sendNotificationChannel(getApplicationContext().getString(R.string.notification_description) + " " + currentQuantity[0]);
                                            }
                                        }
                                        previewsQuantity[0] = currentQuantity[0];
                                    }
                                }

                                @Override
                                public void onFailure(com.quantumcoinwallet.app.api.read.ApiException e) {

                                }
                            });

                            task.execute(taskParams);

                            Thread.sleep(TIME_SLEEP);
                        }
                    } catch (Exception e) {
                        GlobalMethods.ExceptionError(getBaseContext(), TAG, e);
                    }
                }
            };
            thread.start();
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getBaseContext(), TAG, e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(
                    getString(R.string.notification_channel_id), name, importance
            );
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendNotificationChannel(String content) {
        String title = getResources().getString(R.string.notification_title);
        String channelId = getResources().getString(R.string.notification_channel_id);
        int priority = NotificationCompat.PRIORITY_DEFAULT;
        int notificationID = notificationRequestCode;

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationRequestCode,
                intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo))
                        .setContentIntent(pendingIntent)
                        .setPriority(priority)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Since android Oreo notification channel is needed.
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(notificationID, notificationBuilder.build());
    }

    private void loadSeedsThread() {
        try {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            KeyViewModel.getBridge().initializeOffline();
                            String resultJson = KeyViewModel.getBridge().getAllSeedWords();
                            JSONObject outer = new JSONObject(resultJson);
                            if (!outer.optBoolean("success", false)) {
                                throw new RuntimeException("getAllSeedWords bridge call returned failure: "
                                        + outer.optString("error"));
                            }
                            JSONObject data = outer.getJSONObject("data");
                            JSONArray wordsJson = data.getJSONArray("words");
                            ArrayList<String> words = new ArrayList<>(wordsJson.length());
                            HashSet<String> wordSet = new HashSet<>(wordsJson.length() * 2);
                            for (int i = 0; i < wordsJson.length(); i++) {
                                String w = wordsJson.getString(i);
                                words.add(w);
                                wordSet.add(w.toLowerCase());
                            }
                            GlobalMethods.ALL_SEED_WORDS = words;
                            GlobalMethods.SEED_WORD_SET = wordSet;
                            GlobalMethods.seedLoaded = true;
                            return;
                        } catch (Exception e) {
                            android.util.Log.e(TAG, "loadSeedsThread failed, retrying in 1s", e);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            };
            thread.start();
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getBaseContext(), TAG, e);
        }
    }



}