package com.ejemplo.libros;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class App implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient ddb = DynamoDbClient.builder()
            .region(Region.US_EAST_2) // tu región DynamoDB
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String TABLE_NAME = "Libros";

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String httpMethod = (String) event.get("httpMethod");
        Map<String, Object> response;

        try {
            switch (httpMethod) {
                case "POST": // Create
                    response = createBook(event);
                    break;
                case "GET": // Read (One or All)
                    response = getBook(event);
                    break;
                case "PUT": // Update
                    response = updateBook(event);
                    break;
                case "DELETE": // Delete
                    response = deleteBook(event);
                    break;
                default:
                    response = buildResponse(400, "{\"error\":\"Unsupported method\"}");
            }
        } catch (Exception e) {
            response = buildResponse(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
        return response;
    }

    // ✅ Create - POST /items
    private Map<String, Object> createBook(Map<String, Object> event) throws Exception {
        String body = (String) event.get("body");
        Map<String, Object> libro = mapper.readValue(body, Map.class);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(libro.get("id").toString()).build());
        item.put("titulo", AttributeValue.builder().s(libro.get("titulo").toString()).build());
        item.put("autor", AttributeValue.builder().s(libro.get("autor").toString()).build());
        item.put("precio", AttributeValue.builder().n(libro.get("precio").toString()).build());
        item.put("anio", AttributeValue.builder().n(libro.get("anio").toString()).build());

        ddb.putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(item).build());

        return buildResponse(200, "{\"mensaje\":\"Libro insertado con éxito\"}");
    }

    // ✅ Read - GET /items or GET /items/{id}
    private Map<String, Object> getBook(Map<String, Object> event) throws Exception {
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");

        if (pathParams != null && pathParams.containsKey("id")) {
            // GET by ID
            String id = pathParams.get("id");
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("id", AttributeValue.builder().s(id).build()))
                    .build();

            Map<String, AttributeValue> item = ddb.getItem(request).item();
            if (item == null || item.isEmpty()) {
                return buildResponse(404, "{\"error\":\"Libro no encontrado\"}");
            }

            Map<String, Object> libro = convertItem(item);
            return buildResponse(200, mapper.writeValueAsString(libro));
        } else {
            // GET all
            ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).build();
            List<Map<String, AttributeValue>> items = ddb.scan(scanRequest).items();

            List<Map<String, Object>> libros = new ArrayList<>();
            for (Map<String, AttributeValue> item : items) {
                libros.add(convertItem(item));
            }
            return buildResponse(200, mapper.writeValueAsString(libros));
        }
    }

    // ✅ Update - PUT /items
    private Map<String, Object> updateBook(Map<String, Object> event) throws Exception {
        String body = (String) event.get("body");
        Map<String, Object> libro = mapper.readValue(body, Map.class);
        String id = libro.get("id").toString();

        Map<String, AttributeValueUpdate> updates = new HashMap<>();

        if (libro.containsKey("titulo")) {
            updates.put("titulo", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().s(libro.get("titulo").toString()).build())
                    .action(AttributeAction.PUT).build());
        }
        if (libro.containsKey("autor")) {
            updates.put("autor", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().s(libro.get("autor").toString()).build())
                    .action(AttributeAction.PUT).build());
        }
        if (libro.containsKey("precio")) {
            updates.put("precio", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().n(libro.get("precio").toString()).build())
                    .action(AttributeAction.PUT).build());
        }
        if (libro.containsKey("anio")) {
            updates.put("anio", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().n(libro.get("anio").toString()).build())
                    .action(AttributeAction.PUT).build());
        }

        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("id", AttributeValue.builder().s(id).build()))
                .attributeUpdates(updates)
                .build());

        return buildResponse(200, "{\"mensaje\":\"Libro actualizado con éxito\"}");
    }

    // ✅ Delete - DELETE /items/{id}
    private Map<String, Object> deleteBook(Map<String, Object> event) {
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");

        if (pathParams != null && pathParams.containsKey("id")) {
            String id = pathParams.get("id");
            ddb.deleteItem(DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("id", AttributeValue.builder().s(id).build()))
                    .build());
            return buildResponse(200, "{\"mensaje\":\"Libro eliminado con éxito\"}");
        } else {
            return buildResponse(400, "{\"error\":\"Debe proporcionar un id\"}");
        }
    }

    // ✅ Utilitario para mapear AttributeValue a tipos simples
    private Map<String, Object> convertItem(Map<String, AttributeValue> item) {
        Map<String, Object> libro = new HashMap<>();
        item.forEach((k, v) -> {
            if (v.s() != null) libro.put(k, v.s());
            else if (v.n() != null) libro.put(k, v.n());
            else libro.put(k, v.toString());
        });
        return libro;
    }

    // ✅ Construir respuesta en formato API Gateway Proxy
    private Map<String, Object> buildResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", body);
        return response;
    }
}