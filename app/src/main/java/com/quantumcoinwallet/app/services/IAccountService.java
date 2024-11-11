package com.quantumcoinwallet.app.services;

import com.quantumcoinwallet.app.entity.Result;

public interface IAccountService {

    Result<Object> getBalanceByAccount(String address);

    Result<Object> listTxnByAccount(String address, int pageIndex);

    Result<Object> listPendingTxnByAccount(String address, int pageIndex);

    Result<Object> sendTransactionByAccount(String txData);

}
