package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = PasswordUsagesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordPreviousUsages {

    String message() default "This password has been recently used";

    Class<?>[] groups() default {};

    String password();

    Class<? extends Payload>[] payload() default {};
}
