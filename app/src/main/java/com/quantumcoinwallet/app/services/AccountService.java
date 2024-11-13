package com.quantumcoinwallet.app.services;

import android.content.Context;

import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.model.*;
import com.quantumcoinwallet.app.entity.Result;
import com.quantumcoinwallet.app.asynctask.read.*;

import com.quantumcoinwallet.app.api.write.model.*;
import com.quantumcoinwallet.app.asynctask.write.*;

public class AccountService  implements IAccountService
{
    private Result<Object> _objectResult = null ;

    public AccountService(){

    }

    @Override
    public Result<Object> getBalanceByAccount(String address) {

        Context context = null;
        String[] taskParams = { address };

        AccountBalanceRestTask task = new AccountBalanceRestTask(
                null, new AccountBalanceRestTask.TaskListener() {
            @Override
            public void onFinished(BalanceResponse balanceResponse) {
                _objectResult = new  Result<Object>(balanceResponse, null);
            }
            @Override
            public void onFailure(ApiException e) {
                _objectResult = new  Result<Object>(null, e);
            }
        });

        try {
            task.execute(taskParams).wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return  _objectResult;
    }

    @Override
    public  Result<Object> listTxnByAccount(String address, int pageIndex) {

        Context context = null;
        String[] taskParams = { address, String.valueOf(pageIndex) };

        AccountTxnRestTask task = new AccountTxnRestTask(
                context.getApplicationContext(), new AccountTxnRestTask.TaskListener() {
            @Override
            public void onFinished(AccountTransactionSummaryResponse accountTransactionSummaryResponse) {
                _objectResult = new  Result<Object>(accountTransactionSummaryResponse, null);
            }
            @Override
            public void onFailure(com.quantumcoinwallet.app.api.read.ApiException e) {
                _objectResult = new  Result<Object>(null, e);
            }
        });

        try {
            task.execute(taskParams).wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return  _objectResult;
    }

    @Override
    public  Result<Object> listPendingTxnByAccount(String address, int pageIndex) {

        Context context = null;
        String[] taskParams = { address, String.valueOf(pageIndex) };

        AccountPendingTxnRestTask task = new AccountPendingTxnRestTask(
                context.getApplicationContext(), new AccountPendingTxnRestTask.TaskListener() {
            @Override
            public void onFinished(AccountPendingTransactionSummaryResponse accountPendingTransactionSummaryResponse) {
                _objectResult = new  Result<Object>(accountPendingTransactionSummaryResponse, null);
            }
            @Override
            public void onFailure(com.quantumcoinwallet.app.api.read.ApiException e) {
                _objectResult = new  Result<Object>(null, e);
            }
        });

        try {
            task.execute(taskParams).wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return  _objectResult;
    }

    @Override
    public  Result<Object> sendTransactionByAccount(String txData) {

        Context context = null;
        String[] taskParams = { txData };

        TransactionRestTask task = new TransactionRestTask(
                context.getApplicationContext(), new TransactionRestTask.TaskListener() {
            @Override
            public void onFinished(TransactionSummaryResponse transactionSummaryResponse) {
                _objectResult = new  Result<Object>(transactionSummaryResponse, null);
            }

            @Override
            public void onFailure(com.quantumcoinwallet.app.api.write.ApiException e) {
                _objectResult = new  Result<Object>(null, e);
            }
        });

        try {
            task.execute(taskParams).wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return  _objectResult;
    }

}