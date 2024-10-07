package com.nequi.franchises.IO;

import com.nequi.franchises.config.SerializerConfig;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.Serializable;

import static com.nequi.franchises.util.Utils.parsePayload;


public class EventStoreFactory {

    // DynamoDB client creation (can be injected or passed by HOF)
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build(); // Ideally passed as dependency

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
                            "sortKey", item.get("sortKey").s(),
                            "eventType", item.get("eventType").s(),
                            "version", item.get("version").n(),
                            "timestamp", item.get("timestamp").s(),
                            // Mapea el payload como un JSON o HashMap dependiendo de la estructura
                            "payload", parsePayload(item.get("payload").s()),
                            // Metadata también puede ser otro HashMap dependiendo de la estructura
                            "metadata", parsePayload(item.get("metadata").s())
                    ));
        };
    }

    // Función sin argumentos que retorna una Function2
    public static Function2<List<Map<String, Serializable>>, String, List<Map<String, Serializable>>> saveEventsStrongly() {
        return (events, aggregateId) -> {
            List<TransactWriteItem> transactWriteItems = events
                    .map(event -> createTransactWriteItem(aggregateId, event));

            TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactWriteItems.asJava())
                    .build();

            dynamoDbClient.transactWriteItems(transactRequest);  // Esto asegura que las operaciones son ACID

            return events;
        };
    }

    private static TransactWriteItem createTransactWriteItem(String aggregateId, Map<String, Serializable> event) {
        Put put = Put.builder()
                .tableName("Events")
                .item(createPutRequest(aggregateId, event).toJavaMap())
                .build();
        return TransactWriteItem.builder().put(put).build();
    }

    // Función auxiliar para crear una solicitud de PutItem para cada evento
    private static Map<String, AttributeValue> createPutRequest(String aggregateId, Map<String, Serializable> event) {
        return io.vavr.collection.HashMap.of(
                "aggregateId", AttributeValue.builder().s(aggregateId).build(),
                "sortKey", AttributeValue.builder().s(event.get("sortKey").toString()).build(),
                "eventType", AttributeValue.builder().s(event.get("eventType").toString()).build(),
                "version", AttributeValue.builder().n(event.get("version").toString()).build(),
                "eventData", AttributeValue.builder().s(event.get("eventData").toString()).build() // Suponiendo que los datos están serializados en JSON
        );
    }
}
