package com.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Function {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @FunctionName("createOrderData")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,// <-- Optional<String>
        @BlobOutput(
                name = "outputBlob",
                path = "orderscontainers/{sessionId}.json",
                connection = "AzureWebJobsStorage" // This should point to orderservicestore in your settings
        ) OutputBinding<String> outputBlob,
        final ExecutionContext context) {

        String body = request.getBody().orElse("{}");
        Map<String, Object> input = null;
        try {
            input = MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});

        String sessionId = (String) input.get("sessionId");
        Object orderDetails = input.get("orderDetails");
        Object products = input.get("products");

        if (sessionId == null || sessionId.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Missing sessionId in request")
                    .build();
        }

        String now = Instant.now().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("generatedAtUtc", now);
        payload.put("orderDetails", orderDetails);
        payload.put("products", products);

        String jsonPayload =MAPPER.writeValueAsString(payload);
        outputBlob.setValue(jsonPayload);

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonPayload)
                .build();
        } catch (JsonProcessingException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Exception in request")
                    .build();
        }
    }
}