package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = PasswordBlackListValidator.class)
@Target({ ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordBlackListCheck {

    String message() default "Invalid branding value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    boolean isBase64Encoded() default true;
}
