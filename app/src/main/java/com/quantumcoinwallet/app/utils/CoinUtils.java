package com.quantumcoinwallet.app.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Pure Java wei ↔ ether string conversion (1 ether = 10^18 wei).
 * Safe for use on any thread; no Android dependencies.
 */
public final class CoinUtils {

    private static final BigDecimal WEI_PER_ETHER = new BigDecimal("1000000000000000000");
    private static final int ETHER_FRACTION_DIGITS = 18;

    private CoinUtils() {
    }

    /**
     * Converts a decimal string of wei (smallest unit) to a human-readable ether amount.
     *
     * @param weiValue wei as a decimal string; null, empty, or invalid yields "0"
     * @return ether as a plain string without scientific notation; trailing fractional zeros removed
     */
    public static String formatWei(String weiValue) {
        if (weiValue == null) {
            return "0";
        }
        String trimmed = weiValue.trim();
        if (trimmed.isEmpty()) {
            return "0";
        }
        try {
            BigDecimal wei = new BigDecimal(trimmed);
            if (wei.signum() == 0) {
                return "0";
            }
            BigDecimal ether = wei.divide(WEI_PER_ETHER, ETHER_FRACTION_DIGITS, RoundingMode.HALF_UP);
            return ether.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException | ArithmeticException e) {
            return "0";
        }
    }

    /**
     * Converts a human-readable ether amount string to wei (smallest unit) as a decimal string.
     *
     * @param etherValue ether amount; null, empty, or invalid yields "0"
     * @return wei as an integer string (no fractional wei); rounded half-up from ether × 10^18
     */
    public static String parseEther(String etherValue) {
        if (etherValue == null) {
            return "0";
        }
        String trimmed = etherValue.trim();
        if (trimmed.isEmpty()) {
            return "0";
        }
        try {
            BigDecimal ether = new BigDecimal(trimmed);
            if (ether.signum() == 0) {
                return "0";
            }
            BigDecimal wei = ether.multiply(WEI_PER_ETHER);
            BigInteger weiInt = wei.setScale(0, RoundingMode.HALF_UP).toBigInteger();
            return weiInt.toString();
        } catch (NumberFormatException | ArithmeticException e) {
            return "0";
        }
    }
}
