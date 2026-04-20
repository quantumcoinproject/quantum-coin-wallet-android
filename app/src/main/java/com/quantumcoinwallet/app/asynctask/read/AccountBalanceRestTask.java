package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.quantumcoinwallet.app.api.read.ApiClient;
import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.BalanceResponse;
import com.quantumcoinwallet.app.entity.ServiceException;
import com.quantumcoinwallet.app.utils.GlobalMethods;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountBalanceRestTask {

    private final Context context;
    private final TaskListener taskListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AccountBalanceRestTask(Context context, TaskListener listener) {
        this.context = context;
        this.taskListener = listener;
    }

    public void execute(final String... params) {
        // MF-24: snapshot the base URL at task creation so a concurrent
        // network switch cannot rewrite the endpoint we target.
        final String basePathSnapshot = GlobalMethods.SCAN_API_URL;

        // MF-25: validate params before dispatch so we surface bad input
        // as a listener failure rather than NPE/AIOOBE inside the executor.
        if (params == null || params.length < 1 || TextUtils.isEmpty(params[0])) {
            notifyFailure(new ApiException("address parameter is required"));
            return;
        }
        final String address = params[0];

        executor.submit(new Runnable() {
            @Override
            public void run() {
                BalanceResponse balanceResponse = null;
                ApiException apiException = null;
                try {
                    ApiClient apiClient = new ApiClient().setBasePath(basePathSnapshot);
                    AccountsApi apiInstance = new AccountsApi(apiClient);
                    balanceResponse = apiInstance.getAccountBalance(address);
                } catch (ApiException e) {
                    apiException = e;
                }

                final BalanceResponse br = balanceResponse;
                final ApiException ae = apiException;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (taskListener != null) {
                                if (ae == null) {
                                    taskListener.onFinished(br);
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
        void onFinished(BalanceResponse balanceResponse) throws ServiceException;
        void onFailure(ApiException apiException);
    }
}
