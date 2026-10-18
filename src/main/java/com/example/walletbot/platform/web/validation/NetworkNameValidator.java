package com.example.walletbot.platform.web.validation;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NetworkNameValidator implements ConstraintValidator<NetworkName, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return false;
        try {
            BlockchainNetwork.valueOf(value.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}