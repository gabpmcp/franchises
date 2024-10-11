package com.nequi.franchises.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.jackson.datatype.VavrModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.vavr.Function0;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Configuration
public class SerializerConfig {

    // Función memoizada que actúa como Singleton
    // Memoización del ObjectMapper con módulo de Vavr y deserializador de mapas anidados
    private static final Function0<ObjectMapper> getObjectMapper = Function0.of(() ->
            new ObjectMapper()
                    .registerModule(new VavrModule())
                    .registerModule(new SimpleModule().addDeserializer(Map.class, new JsonDeserializer<Map<?, ?>>() {
                                @Override
                                public Map<?, ?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                                    LinkedHashMap<?, ?> linkedHashMap = p.readValueAs(LinkedHashMap.class);
                                    return (Map<?, ?>) convertValue(linkedHashMap); // Convertir usando lógica común
                            }})
                            .addDeserializer(List.class, new JsonDeserializer<List<?>>() {
                                @Override
                                public List<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                                    ArrayList<?> arrayList = p.readValueAs(ArrayList.class);
                                    return (List<?>) convertValue(arrayList); // Convertir usando lógica común
                                }
                            }))).memoized();

    public static ObjectMapper mapper = getObjectMapper.apply();

    @Configuration
    static class JacksonConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return SerializerConfig.mapper;
        }
    }

    // Lógica común para conversión recursiva de mapas y listas
    private static Object convertValue(Object value) {
        if (value instanceof LinkedHashMap) {
            return HashMap.ofAll(((LinkedHashMap<?, ?>) value).entrySet().stream()
                    .map(entry -> java.util.Map.entry(entry.getKey(), convertValue(entry.getValue())))
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue)));
        } else if (value instanceof ArrayList) {
            return List.ofAll(((ArrayList<?>) value).stream().map(SerializerConfig::convertValue));
        }
        return value; // Si no es ni mapa ni lista, devolver el valor tal como está
    }
}