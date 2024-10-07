package com.nequi.franchises.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.jackson.datatype.VavrModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.vavr.Function0;

@Configuration
public class SerializerConfig {

    // Función memoizada que actúa como Singleton
    private static final Function0<ObjectMapper> getObjectMapper = Function0.of(() -> new ObjectMapper().registerModule(new VavrModule())).memoized();

    public static ObjectMapper mapper = getObjectMapper.apply();

    @Configuration
    public static class JacksonConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return SerializerConfig.mapper;
        }
    }
}
