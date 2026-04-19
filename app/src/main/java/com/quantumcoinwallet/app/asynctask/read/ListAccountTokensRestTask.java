package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.AccountTokenListResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListAccountTokensRestTask {

    private final Context context;
    private final TaskListener taskListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ListAccountTokensRestTask(Context context, TaskListener listener) {
        this.context = context;
        this.taskListener = listener;
    }

    public void execute(final String... params) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                AccountTokenListResponse rsp = null;
                ApiException apiException = null;
                try {
                    String address = params[0];
                    int pageIndex = Integer.parseInt(params[1]);
                    AccountsApi apiInstance = new AccountsApi();
                    rsp = apiInstance.listAccountTokens(address, pageIndex);
                } catch (ApiException e) {
                    apiException = e;
                }

                final AccountTokenListResponse finalRsp = rsp;
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
        void onFinished(AccountTokenListResponse accountTokenListResponse);
        void onFailure(ApiException apiException);
    }
}
