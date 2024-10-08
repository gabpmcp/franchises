package com.nequi.franchises.IO;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.io.Serializable;
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
                    .tableName("Events")  // Nombre de la tabla DynamoDB
                    .keyConditionExpression("aggregateId = :hash")  // Condición para verificar si existe el hash
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

            // Convertir los resultados de DynamoDB a la estructura de eventos esperada
            return List.ofAll(response.items().stream())
                    .map(item -> HashMap.of(
                            "aggregateId", item.get("aggregateId").s(),
                            "version", item.get("version").n(),
                            "type", item.get("type").s(),
                            "timestamp", item.get("timestamp").s(),
                            // Mapea el payload como un JSON o HashMap dependiendo de la estructura
                            "payload", HashMap.ofAll(item.get("payload").m()).mapValues(AttributeValue::s),
                            // Metadata también puede ser otro HashMap dependiendo de la estructura
                            "metadata", HashMap.ofAll(item.get("metadata").m()).mapValues(AttributeValue::s)
                    ));
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
        return result.items().isEmpty() ? 0 : Integer.parseInt(result.items().getFirst().get("sortKey").n());
    }

    // Función sin argumentos que retorna una Function2
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

    private static TransactWriteItem createTransactWriteItem(String aggregateId, Tuple2<Map<String, Serializable>, Integer> versionedEvent) {
        Put put = Put.builder()
                .tableName("Events")
                .item(createPutRequest(aggregateId, versionedEvent._2(), versionedEvent._1()).toJavaMap())
                .build();
        return TransactWriteItem.builder().put(put).build();
    }

    // Función auxiliar para crear una solicitud de PutItem para cada evento
    private static Map<String, AttributeValue> createPutRequest(String aggregateId, int version, Map<String, Serializable> event) {
        return io.vavr.collection.HashMap.of(
                "aggregateId", AttributeValue.builder().s(aggregateId).build(),
                "timestamp", AttributeValue.builder().s(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).build(),
                "type", AttributeValue.builder().s(getValue(event, "type", "")).build(),
                "version", AttributeValue.builder().n(String.valueOf(version)).build(),
                "payload", AttributeValue.builder().m(getValue(event, "payload", HashMap.empty()).map((k, v) -> Tuple.of(k.toString(), AttributeValue.fromS(v.toString()))).toJavaMap()).build(),
                "metadata", AttributeValue.builder().m(getValue(event, "metadata", HashMap.empty()).map((k, v) -> Tuple.of(k.toString(), AttributeValue.fromS(v.toString()))).toJavaMap()).build() // Suponiendo que los datos están serializados en JSON
        );
    }
}
