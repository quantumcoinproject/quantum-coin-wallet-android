package com.quantumcoin.app.gas;

/**
 * Desktop per-kind gas defaults and estimate buffers for the two
 * transactions this wallet submits (desktop src/app/gas.ts):
 * coin send 21000 / 0% buffer, token send 84000 / +10%.
 */
public enum GasKind {
    SEND_COIN("sendCoin", 21000L, 0),
    SEND_TOKEN("sendToken", 84000L, 10);

    public final String txKind;
    public final long defaultGasLimit;
    public final int bufferPercent;

    GasKind(String txKind, long defaultGasLimit, int bufferPercent) {
        this.txKind = txKind;
        this.defaultGasLimit = defaultGasLimit;
        this.bufferPercent = bufferPercent;
    }

    /** Desktop applyGasBuffer: floor(raw * (100 + pct) / 100). */
    public long applyBuffer(long raw) {
        return (raw * (100L + bufferPercent)) / 100L;
    }
}
