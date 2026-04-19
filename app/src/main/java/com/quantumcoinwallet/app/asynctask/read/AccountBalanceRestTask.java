package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.BalanceResponse;
import com.quantumcoinwallet.app.entity.ServiceException;

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
        executor.submit(new Runnable() {
            @Override
            public void run() {
                BalanceResponse balanceResponse = null;
                ApiException apiException = null;
                try {
                    String address = params[0];
                    AccountsApi apiInstance = new AccountsApi();
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

    public interface TaskListener {
        void onFinished(BalanceResponse balanceResponse) throws ServiceException;
        void onFailure(ApiException apiException);
    }
}
