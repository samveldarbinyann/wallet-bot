package com.example.walletbot.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HashingService {

    private static final int MIN_PEPPER_LENGTH = 16;

    private final byte[] otpPepperBytes;

    public HashingService(@Value("${security.pin-pepper}") String pepper) {
        if (pepper == null || pepper.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException(
                    "security.pin-pepper must be at least " + MIN_PEPPER_LENGTH + " characters");
        }
        this.otpPepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hashOtp(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpPepperBytes, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    public boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String hashEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpPepperBytes, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}