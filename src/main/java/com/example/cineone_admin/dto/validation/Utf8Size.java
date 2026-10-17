package com.example.cineone_admin.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** MySQL TEXT is bounded in bytes, not Java UTF-16 characters. Null is allowed. */
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = Utf8SizeValidator.class)
public @interface Utf8Size {
    String message() default "must not exceed {max} UTF-8 bytes";
    int max() default 65535;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
