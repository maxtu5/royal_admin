package com.tuiken.royaladmin.services;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaConsumer {

    @Autowired
    private KafkaListenerEndpointRegistry registry;
    private final DataFeedService dataFeedService;

    @KafkaListener(
            topics = "test-topic",
            id = "serial-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message) {
        System.out.println("📥 Received: " + message);
        try {
            dataFeedService.resolveUnusedCacheRecord(extractUrl(message));
            Thread.sleep(30000);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private String extractUrl(String json) {
        JSONObject obj = new JSONObject(json);
        return obj.getString("url");
    }

}
