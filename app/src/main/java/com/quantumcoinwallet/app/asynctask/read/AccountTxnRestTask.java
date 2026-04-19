package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.quantumcoinwallet.app.api.read.ApiClient;
import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummaryResponse;
import com.quantumcoinwallet.app.utils.GlobalMethods;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountTxnRestTask {

    private final Context context;
    private final TaskListener taskListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AccountTxnRestTask(Context context, TaskListener listener) {
        this.context = context;
        this.taskListener = listener;
    }

    public void execute(final String... params) {
        final String basePathSnapshot = GlobalMethods.SCAN_API_URL;

        if (params == null || params.length < 2
                || TextUtils.isEmpty(params[0]) || TextUtils.isEmpty(params[1])) {
            notifyFailure(new ApiException("address and pageIndex are required"));
            return;
        }
        final String address = params[0];
        final int pageIndex;
        try {
            pageIndex = Integer.parseInt(params[1]);
            if (pageIndex < 0) throw new NumberFormatException("negative");
        } catch (NumberFormatException nfe) {
            notifyFailure(new ApiException("pageIndex must be a non-negative integer"));
            return;
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
                AccountTransactionSummaryResponse accountTransactionSummaryResponse = null;
                ApiException apiException = null;
                try {
                    ApiClient apiClient = new ApiClient().setBasePath(basePathSnapshot);
                    AccountsApi apiInstance = new AccountsApi(apiClient);
                    accountTransactionSummaryResponse = apiInstance.listAccountTransactions(
                            address, pageIndex);
                } catch (ApiException e) {
                    apiException = e;
                }

                final AccountTransactionSummaryResponse rsp = accountTransactionSummaryResponse;
                final ApiException ae = apiException;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (taskListener != null) {
                                if (ae == null) {
                                    taskListener.onFinished(rsp);
                                } else {
                                    taskListener.onFailure(ae);
                                }
                            }
                        } catch (Exception ignore) {
                        } finally {
                            executor.shutdown();
                        }
                    }
                });
            }
        });
    }

    private void notifyFailure(final ApiException ae) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (taskListener != null) taskListener.onFailure(ae);
                } finally {
                    executor.shutdown();
                }
            }
        });
    }

    public interface TaskListener {
        void onFinished(AccountTransactionSummaryResponse accountTransactionSummaryResponse);
        void onFailure(ApiException apiException);
    }
}
