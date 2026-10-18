package com.example.walletbot.platform.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NetworkNameValidator.class)
public @interface NetworkName {
    String message() default "Unknown blockchain network";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}