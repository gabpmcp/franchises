package com.nequi.franchises.util;

import io.vavr.Function1;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import reactor.core.publisher.Mono;

import java.io.Serializable;

public interface Step extends Function1<Map<String, Serializable>, Mono<HashMap<String, Serializable>>> {
}
