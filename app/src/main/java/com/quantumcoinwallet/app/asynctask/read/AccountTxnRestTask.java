package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummaryResponse;

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
        executor.submit(new Runnable() {
            @Override
            public void run() {
                AccountTransactionSummaryResponse accountTransactionSummaryResponse = null;
                ApiException apiException = null;
                try {
                    String address = params[0];
                    int pageindex = Integer.parseInt(params[1]);
                    AccountsApi apiInstance = new AccountsApi();
                    accountTransactionSummaryResponse = apiInstance.listAccountTransactions(
                            address, pageindex);
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

    public interface TaskListener {
        void onFinished(AccountTransactionSummaryResponse accountTransactionSummaryResponse);
        void onFailure(ApiException apiException);
    }
}
