package com.nequi.franchises.util;

import io.vavr.collection.Map;

import java.io.Serializable;

public record Error(String type, Map<String, Serializable> errors) { }
