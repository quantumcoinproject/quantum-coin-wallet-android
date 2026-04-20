package com.quantumcoinwallet.app.utils;

import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummary;
import com.quantumcoinwallet.app.api.read.model.Receipt;

/**
 * Display rules for the transaction list, aligned with the desktop wallet
 * ({@code refreshTransactionListInner} in {@code app.js}): in/out from {@code from}
 * vs wallet, success when {@code status == "0x1"}, failed rows show an alert icon
 * next to the direction arrow; pending list always uses the outgoing (up) icon.
 */
public final class AccountTransactionUi {

    private AccountTransactionUi() {
    }

    /**
     * Mirrors desktop: {@code txn.status !== null && txn.status == "0x1"}; if the
     * root field is absent, fall back to {@link Receipt#getStatus()}.
     */
    public static boolean isCompletedSuccessful(AccountTransactionSummary txn) {
        if (txn == null) {
            return false;
        }
        Object st = txn.getStatus();
        if (st != null) {
            return "0x1".equalsIgnoreCase(String.valueOf(st).trim());
        }
        Receipt receipt = txn.getReceipt();
        if (receipt != null && receipt.getStatus() != null) {
            return "0x1".equalsIgnoreCase(String.valueOf(receipt.getStatus()).trim());
        }
        return false;
    }
}
