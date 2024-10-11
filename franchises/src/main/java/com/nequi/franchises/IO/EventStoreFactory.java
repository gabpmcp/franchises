package com.nequi.franchises.IO;

import com.nequi.franchises.util.Utils;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static com.nequi.franchises.util.Utils.getValue;

public class EventStoreFactory {

    // DynamoDB client creation (can be injected or passed by HOF)
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build(); // Ideally passed as dependency

    // Consulta a DynamoDB para obtener todos los aggregateId
    public static Function1<String, Boolean> checkIfHashExistsInDynamo() {
        return hash -> {
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName("Idempotency")  // Nombre de la tabla DynamoDB
                    .keyConditionExpression("hashCommand = :hash")  // Condición para verificar si existe el hash
                    .expressionAttributeValues(HashMap.of(":hash", AttributeValue.builder().s(hash).build()).toJavaMap())
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            // Si hay algún item, el hash ya existe
            return !response.items().isEmpty();
        };
    }

    // Función sin argumentos que retorna una Function1
    public static Function1<String, List<Map<String, Object>>> fetchEventsFromDynamo() {
        return aggregateId -> {
            // Configurar la solicitud de consulta a DynamoDB (ajusta los nombres de tablas y atributos según tu diseño)
            QueryRequest queryRequest = QueryRequest.builder()
                .tableName("Events") // Nombre de la tabla en DynamoDB
                .keyConditionExpression("aggregateId = :aggregateId")
                .expressionAttributeValues(HashMap.of(":aggregateId", AttributeValue.builder().s(aggregateId).build()).toJavaMap())
                .consistentRead(true)
                .build();

            // Ejecutar la consulta en DynamoDB
            QueryResponse response = dynamoDbClient.query(queryRequest);

            return List.ofAll(response.items().stream())
                .map(item -> HashMap.of(
                    "aggregateId", item.get("aggregateId").s(),
                    "version", item.get("version").n(),
                    "type", item.get("type").s(),
                    "timestamp", item.get("timestamp").s(),
                    // Mapea el payload como un JSON o HashMap dependiendo de la estructura
                    "payload", HashMap.ofAll(item.get("payload").m()).mapValues(EventStoreFactory::convertAttributeValue),
                    // Metadata también puede ser otro HashMap dependiendo de la estructura
                    "metadata", HashMap.ofAll(item.get("metadata").m()).mapValues(EventStoreFactory::convertAttributeValue)
                ));
        };
    }

    // Función que convierte un AttributeValue en un tipo de Vavr correspondiente
    public static Object convertAttributeValue(AttributeValue value) {
        return switch (value.type()) {
            case M ->
                    HashMap.ofAll(value.m()).mapValues(EventStoreFactory::convertAttributeValue);   // Recursivamente para Map
            case N -> Double.parseDouble(value.n());                               // Convertir números a Double
            case S -> value.s();                                                   // String
            case BOOL -> value.bool();                                             // Booleano
            case L ->
                    List.ofAll(value.l().stream().map(EventStoreFactory::convertAttributeValue));   // Lista de valores, recursiva
            case NS -> List.ofAll(value.ns().stream().map(Double::parseDouble));   // Lista de números (String)
            case SS -> List.ofAll(value.ss());                                     // Lista de Strings
            case BS -> List.ofAll(value.bs());                                     // Lista de Binary
            case B -> value.b();                                                   // Binary
//        case NULL -> null;                                                     // Valores nulos
            default -> value;                                                      // Otros valores por defecto
        };
    }

    public static int getMaxVersionForAggregate(String aggregateId) {
        QueryRequest queryRequest = QueryRequest.builder()
            .tableName("Events")
            .keyConditionExpression("aggregateId = :aggId")
            .expressionAttributeValues(java.util.Map.of(":aggId", AttributeValue.builder().s(aggregateId).build()))
            .scanIndexForward(false)
            .limit(1)
            .build();

        QueryResponse result = dynamoDbClient.query(queryRequest);
        return result.items().isEmpty() ? 0 : Integer.parseInt(result.items().getFirst().get("version").n());
    }

    public static Function2<List<Map<String, Serializable>>, String, List<Map<String, Serializable>>> saveEventsStrongly() {
        return (events, aggregateId) -> {
            int maxEvent = getMaxVersionForAggregate(aggregateId);
            var versionedEvents = events.zipWithIndex((m, index) -> Tuple.of(m, maxEvent + ++index));
            List<TransactWriteItem> transactWriteItems = versionedEvents
                    .map(event -> createTransactWriteItem(aggregateId, event));

            TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactWriteItems.asJava())
                    .build();

            dynamoDbClient.transactWriteItems(transactRequest);  // Esto asegura que las operaciones son ACID

            return events;
        };
    }

    // Función sin argumentos que retorna una Function2
    public static Function2<String, String, Map<String, Serializable>> createAggregate() {
        return (hash, aggregateId) -> {
            TransactWriteItem transactWriteItems = createTransactWriteItem(aggregateId, hash);

            TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactWriteItems)
                    .build();

            dynamoDbClient.transactWriteItems(transactRequest);  // Esto asegura que las operaciones son ACID

            return HashMap.of("aggregateId", aggregateId, "hash", hash);
        };
    }

    private static TransactWriteItem createTransactWriteItem(String aggregateId, String hash) {
        Put put = Put.builder()
                .tableName("Idempotency")
                .item(HashMap.of("aggregateId", AttributeValue.builder().s(aggregateId).build(), "hashCommand", AttributeValue.builder().s(hash).build()).toJavaMap())
                .build();
        return TransactWriteItem.builder().put(put).build();
    }

    private static TransactWriteItem createTransactWriteItem(String aggregateId, Tuple2<Map<String, Serializable>, Integer> versionedEvent) {
        Put put = Put.builder()
                .tableName("Events")
                .item(putEventRequest(aggregateId, versionedEvent._2(), versionedEvent._1()).toJavaMap())
                .build();
        return TransactWriteItem.builder().put(put).build();
    }

    // Función auxiliar para crear una solicitud de PutItem para cada evento
    private static Map<String, AttributeValue> putEventRequest(String aggregateId, int version, Map<String, Serializable> event) {
        return HashMap.of(
                "aggregateId", AttributeValue.builder().s(aggregateId).build(),
                "timestamp", AttributeValue.builder().s(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).build(),
                "type", AttributeValue.builder().s(getValue(event, "type", "")).build(),
                "version", AttributeValue.builder().n(String.valueOf(version)).build(),
                "payload", AttributeValue.builder().m(convertToAttributeValueMap(getValue(event, "payload", HashMap.empty()))).build(),
                "metadata", AttributeValue.builder().m(convertToAttributeValueMap(getValue(event, "metadata", HashMap.empty()))).build() // Suponiendo que los datos están serializados en JSON
        );
    }

    // Función auxiliar para convertir un valor en el AttributeValue correspondiente
    @SuppressWarnings("unchecked")
    public static AttributeValue convertToAttributeValue(Object value) {
        return switch (value) {
            case Map<?, ?> m -> AttributeValue.builder().m(convertToAttributeValueMap((Map<String, Serializable>) m)).build();
            case List<?> l -> AttributeValue.builder().l(l.map(EventStoreFactory::convertToAttributeValue).toJavaList()).build();
            case String s -> AttributeValue.builder().s(s).build();
            case Number n -> AttributeValue.builder().n(n.toString()).build();
            case Boolean b -> AttributeValue.builder().bool(b).build();
            case ByteBuffer b -> AttributeValue.builder().b(SdkBytes.fromByteBuffer(b)).build(); // Convertir ByteBuffer a SdkBytes
            case null -> AttributeValue.builder().nul(true).build();
            default -> AttributeValue.builder().s(value.toString()).build();
        };
    }


    // Función auxiliar para convertir un Vavr HashMap en un Map de AttributeValue, manejando mapas anidados
    private static java.util.Map<String, AttributeValue> convertToAttributeValueMap(Map<String, Serializable> map) {
        return map.map((k, v) -> Tuple.of(k, convertToAttributeValue(v))).toJavaMap();
    }
}
