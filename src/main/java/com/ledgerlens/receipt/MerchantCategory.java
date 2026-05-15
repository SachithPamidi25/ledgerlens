package com.ledgerlens.receipt;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MerchantCategory {
    FOOD,
    TRANSPORT,
    SHOPPING,
    ENTERTAINMENT,
    HEALTH,
    UTILITIES,
    OTHER;

    @JsonCreator
    public static MerchantCategory fromString(String value) {
        if (value == null) return OTHER;
        try {
            return MerchantCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
