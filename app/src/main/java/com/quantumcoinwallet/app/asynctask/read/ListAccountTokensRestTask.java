package com.quantumcoinwallet.app.asynctask.read;

import android.content.Context;
import android.os.AsyncTask;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.api.AccountsApi;
import com.quantumcoinwallet.app.api.read.model.AccountTokenListResponse;

public class ListAccountTokensRestTask extends AsyncTask<String, Void, Void> {

    private AccountTokenListResponse accountTokenListResponse;
    private Context context;
    private TaskListener taskListener;
    private ApiException apiException;

    public ListAccountTokensRestTask(Context context,
                                     TaskListener listener) {
        this.context = context;
        this.taskListener = listener;
    }

    @Override
    public void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    public Void doInBackground(String... params) {
        String address = params[0];
        int pageIndex = Integer.valueOf(params[1]);

        AccountsApi apiInstance = new AccountsApi();
        try {
            accountTokenListResponse = apiInstance.listAccountTokens(address, pageIndex);
        } catch (ApiException e) {
            apiException = e;
        }
        return null;
    }

    @Override
    public void onPostExecute(Void result) {
        super.onPostExecute(result);
        try {
            if (this.taskListener != null) {
                if (apiException == null) {
                    this.taskListener.onFinished(this.accountTokenListResponse);
                } else {
                    this.taskListener.onFailure(apiException);
                }
            }
        } catch (Exception e) {
        }
    }

    public interface TaskListener {
        void onFinished(AccountTokenListResponse accountTokenListResponse);
        void onFailure(ApiException apiException);
    }
}
