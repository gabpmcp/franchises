package com.nequi.franchises.comands;

import io.vavr.Function1;

public interface Validator extends Function1<Command, ValidationResult> {}
