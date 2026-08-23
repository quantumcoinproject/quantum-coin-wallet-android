package com.quantumcoin.app.gas;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.quantumcoin.app.bridge.BridgeCallback;
import com.quantumcoin.app.utils.GlobalMethods;
import com.quantumcoin.app.viewmodel.KeyViewModel;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * RPC gas estimate through the bridge's {@code estimateGas} handler
 * with the desktop buffer applied (0% coin, +10% token), falling back
 * to the kind default on ANY failure so the caller never fails because
 * an estimate failed. Desktop: src/app/gas.ts estimateGasForContext.
 */
public final class GasEstimator {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GasEstimator() { }

    public interface Callback {
        void onEstimated(long gasLimit, String feeNumber, boolean usedFallback,
                         String error, JSONObject extra);
    }

    public static final class Resolved {
        public final long gasLimit;
        public final String feeNumber;
        Resolved(long gasLimit, String feeNumber) {
            this.gasLimit = gasLimit;
            this.feeNumber = feeNumber;
        }
        public String feeLabel() { return feeNumber + " " + GasFee.FEE_UNIT; }
    }

    /**
     * @param kindPayload kind-specific fields ({@code toAddress} + {@code value}
     *                    for a coin send; {@code toAddress}, {@code contractAddress},
     *                    {@code amount} for a token send); chain fields are added here.
     */
    public static void estimate(final Context ctx, final String walletAddress,
                                final GasKind kind, final JSONObject kindPayload,
                                final boolean unusedPairExists, final Callback cb) {
        final long fallback = kind.defaultGasLimit;
        try {
            JSONObject payload = new JSONObject();
            if (kindPayload != null) {
                Iterator<String> keys = kindPayload.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    payload.put(k, kindPayload.get(k));
                }
            }
            payload.put("txKind", kind.txKind);
            payload.put("fromAddress", walletAddress);
            payload.put("rpcEndpoint", GlobalMethods.RPC_ENDPOINT_URL);
            payload.put("chainId", Integer.parseInt(GlobalMethods.NETWORK_ID));
            KeyViewModel.getBridge().estimateGasAsync(payload, new BridgeCallback() {
                @Override public void onResult(final String jsonResult) {
                    MAIN.post(() -> {
                        try {
                            JSONObject data = new JSONObject(jsonResult).getJSONObject("data");
                            long raw = Long.parseLong(data.getString("gasLimit"));
                            if (raw <= 0) throw new IllegalStateException("zero estimate");
                            long buffered = kind.applyBuffer(raw);
                            cb.onEstimated(buffered,
                                    GasFee.feeNumberFor(ctx, walletAddress, buffered),
                                    false, null, data);
                        } catch (Exception e) {
                            cb.onEstimated(fallback,
                                    GasFee.feeNumberFor(ctx, walletAddress, fallback),
                                    true, e.getMessage(), null);
                        }
                    });
                }
                @Override public void onError(final String error) {
                    MAIN.post(() -> cb.onEstimated(fallback,
                            GasFee.feeNumberFor(ctx, walletAddress, fallback),
                            true, error, null));
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> cb.onEstimated(fallback,
                    GasFee.feeNumberFor(ctx, walletAddress, fallback), true, e.getMessage(), null));
        }
    }

    /** Desktop resolveGasForTx: a positive state limit (estimate or
     *  manual override) wins; otherwise the kind default. */
    public static Resolved resolve(Context ctx, String walletAddress, GasState state,
                                   GasKind kind, boolean unusedPairExists) {
        if (state != null && state.gasLimit != null && state.gasLimit > 0) {
            String fee = state.gasFeeNumber != null
                    ? state.gasFeeNumber
                    : GasFee.feeNumberFor(ctx, walletAddress, state.gasLimit);
            return new Resolved(state.gasLimit, fee);
        }
        long d = kind.defaultGasLimit;
        return new Resolved(d, GasFee.feeNumberFor(ctx, walletAddress, d));
    }
}
