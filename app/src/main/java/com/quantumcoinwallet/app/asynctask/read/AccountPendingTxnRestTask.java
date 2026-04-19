package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.AccountPendingTransactionSummaryResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountPendingTxnRestTask {

    private final Context context;
    private final TaskListener taskListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AccountPendingTxnRestTask(Context context, TaskListener listener) {
        this.context = context;
        this.taskListener = listener;
    }

    public void execute(final String... params) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                AccountPendingTransactionSummaryResponse rsp = null;
                ApiException apiException = null;
                try {
                    String address = params[0];
                    int pageindex = Integer.parseInt(params[1]);
                    AccountsApi apiInstance = new AccountsApi();
                    rsp = apiInstance.listAccountPendingTransactions(address, pageindex);
                } catch (ApiException e) {
                    apiException = e;
                }

                final AccountPendingTransactionSummaryResponse finalRsp = rsp;
                final ApiException ae = apiException;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (taskListener != null) {
                                if (ae == null) {
                                    taskListener.onFinished(finalRsp);
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
        void onFinished(AccountPendingTransactionSummaryResponse accountPendingTransactionSummaryResponse);
        void onFailure(ApiException apiException);
    }
}
