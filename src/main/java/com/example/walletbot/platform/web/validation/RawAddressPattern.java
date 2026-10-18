package com.example.walletbot.platform.web.validation;

public final class RawAddressPattern {

    public static final String REGEX = "^[A-Za-z0-9:+/=_\\-]{10,128}$";

    public static final String MESSAGE = "Address contains illegal characters";

    private RawAddressPattern() {
    }
}
