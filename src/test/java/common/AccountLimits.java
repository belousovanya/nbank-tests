package common;

import java.math.BigDecimal;

public final class AccountLimits {
    private AccountLimits() {}

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    public static final BigDecimal MAX_DEPOSIT = new BigDecimal("5000.00");
    public static final BigDecimal MAX_TRANSFER = new BigDecimal("10000.00");
}