package com.cloud.azurefunction;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class Function {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("OrderItemsReserver")
    public void run(
            @ServiceBusQueueTrigger(
                    name = "message",
                    queueName = "ordertransfer",
                    connection = "ServiceBusConnection"
            ) String body,
            final ExecutionContext context
    ) {
        try {
            Map<String, Object> input = MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});

            String sessionId = (String) input.get("sessionId");
            Object orderDetails = input.get("orderDetails");
            Object products = input.get("products");

            if (sessionId == null || sessionId.isBlank()) {
                context.getLogger().severe("Missing sessionId in message");
                return;
            }

            String now = Instant.now().toString();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("status", "RESERVED");
            payload.put("generatedAtUtc", now);
            payload.put("orderDetails", orderDetails);
            payload.put("products", products);

            String connectionString = System.getenv("AzureWebJobsStorage");
            String containerName = Optional.ofNullable(System.getenv("RESERVATION_CONTAINER"))
                    .filter(v -> !v.isBlank())
                    .orElse("queueorders");

            if (connectionString == null || connectionString.isBlank()) {
                context.getLogger().severe("Missing app setting: AzureWebJobsStorage");
                return;
            }

            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            BlobContainerClient containerClient = serviceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }

            String blobName = "queueorders/" + sessionId + ".json";
            String json = MAPPER.writeValueAsString(payload);

            BlobClient blobClient = containerClient.getBlobClient(blobName);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            int maxAttempts = 3;
            int attempt = 0;
            boolean success = false;
            Exception lastException = null;
            while (attempt < maxAttempts && !success) {
                try {
                    blobClient.upload(new ByteArrayInputStream(bytes), bytes.length, true);
                    success = true;
                } catch (Exception uploadEx) {
                    attempt++;
                    lastException = uploadEx;
                    context.getLogger().warning("Attempt " + attempt + " to upload blob failed: " + uploadEx.getMessage());
                    if (attempt >= maxAttempts) {
                        context.getLogger().severe("All attempts to upload blob failed for sessionId: " + sessionId);
                        throw uploadEx;
                    }
                }
            }

            context.getLogger().info("Reservation stored for sessionId: " + sessionId);

        } catch (Exception e) {
            context.getLogger().severe("OrderItemsReserver failed: " + e.getMessage());
            throw new RuntimeException("Blob storage failed: " + e.getMessage(), e); // Ensure Service Bus will not complete the message
        }
    }
}