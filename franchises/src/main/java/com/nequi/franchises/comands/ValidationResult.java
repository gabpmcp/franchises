package com.nequi.franchises.comands;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;

import java.io.Serializable;

public record ValidationResult(boolean isValid, List<String> errors) {
    public Map<String, Serializable> toMap() {
        return HashMap.of("isValid", isValid, "errors", errors);
    }
}