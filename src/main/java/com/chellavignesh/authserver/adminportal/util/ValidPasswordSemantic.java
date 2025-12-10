package com.chellavignesh.authserver.adminportal.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = PasswordSemanticValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordSemantic {
    String message() default "Invalid password";
    Class<?>[] groups() default {};
    String firstName();
    String lastName();
    String email();
    String username();
    Class<? extends Payload>[] payload() default {};
}
