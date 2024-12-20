package com.nequi.franchises.comands;

import io.vavr.collection.List;

import java.util.regex.Pattern;

import static com.nequi.franchises.util.Utils.getValue;

public class Validators {
    public static Validator required(String field) {
        return (input) -> {
            Object value = input.getAs(field, null);
            if (value == null || value.toString().isEmpty() || value.toString().isBlank()) {
                return new ValidationResult(false, List.of("%s is required".formatted(field)));
            }
            return new ValidationResult(true, List.of());
        };
    }

    public static Validator matchesPattern(String field, String regex) {
        return (input) -> {
            String value = input.getAs(field, "");  // Usa getAs para obtener el valor del campo como String
            if (value == null || !Pattern.matches(regex, value)) {
                return new ValidationResult(false, List.of("%s does not match the required pattern %s".formatted(field, regex)));
            }
            return new ValidationResult(true, List.of());
        };
    }

    public static Validator minLength(String field, int minLength) {
        return (input) -> {
            String value = input.getAs(field, "");
            if (value == null || value.length() >= minLength) {
                return new ValidationResult(true, List.of());
            } else {
                return new ValidationResult(false, List.of("%s must have at least %s characters".formatted(field, minLength)));
            }
        };
    }

    public static Validator isPositive(String field) {
        return (input) -> {
            try {
                int value = Integer.parseInt(input.getAs(field, ""));
                if (value <= 0) {
                    return new ValidationResult(false, List.of("%s must be a positive number".formatted(field)));
                }
                return new ValidationResult(true, List.of());
            } catch (NumberFormatException e) {
                return new ValidationResult(false, List.of("%s must be a numeric value".formatted(field)));
            }
        };
    }

    public static Validator maxLength(String field, int maxLength) {
        return (input) -> {
            String value = input.getAs(field, "");
            if (value != null && value.length() > maxLength) {
                return new ValidationResult(false, List.of("%s must have no more than %s characters".formatted(field, maxLength)));
            }
            return new ValidationResult(true, List.of());
        };
    }

    public static Validator isNonEmptyString(String field) {
        return (input) -> {
            String value = input.getAs(field, "");
            if (value == null || value.trim().isEmpty()) {
                return new ValidationResult(false, List.of("%s must be a non-empty string".formatted(field)));
            }
            return new ValidationResult(true, List.of());
        };
    }

    public static Validator isNumeric(String field) {
        return input -> {
            Object value = input.getAs(field, "");
            return value instanceof Integer || (value instanceof String s && s.matches("-?\\d+"))
                    ? new ValidationResult(true, List.of())
                    : new ValidationResult(false, List.of("%s must be a numeric value".formatted(field)));
        };
    }

    public static Validator isUUID(String field) {
        return (input) -> {
            try {
                java.util.UUID.fromString(input.getAs(field, ""));
                return new ValidationResult(true, List.of());
            } catch (IllegalArgumentException e) {
                return new ValidationResult(false, List.of("%s must be a valid UUID".formatted(field)));
            }
        };
    }

    public static Validator isInRange(String field, int min, int max) {
        return (input) -> {
            int value = input.getAs(field, 0);
            if (value < min || value > max) {
                return new ValidationResult(false, List.of("%s must be between %s and %s".formatted(field, min, max)));
            }
            return new ValidationResult(true, List.of());
        };
    }

    public static Validator isOneOf(String field, String... validValues) {
        return (input) -> {
            String value = input.getAs(field, "");
            if (!List.of(validValues).contains(value)) {
                return new ValidationResult(false, List.of("%s must be one of %s".formatted(field, String.join(", ", validValues))));
            }
            return new ValidationResult(true, List.of());
        };
    }
}
