package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValidationTypeValidator.class)
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidValidationType {

    String message() default "Invalid validation_type value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
