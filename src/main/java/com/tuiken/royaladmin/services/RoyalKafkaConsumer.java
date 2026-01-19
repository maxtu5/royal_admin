package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.model.entities.Monarch;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Service;

//@Service
@RequiredArgsConstructor
public class RoyalKafkaConsumer implements ConsumerSeekAware {

    private final DataFeedService dataFeedService;
    private final KafkaConsumer<String, String> offsetConsumer;
    private final KafkaListenerEndpointRegistry registry;

    @KafkaListener(
            topics = "unused-cache-topic",
            groupId = "unused-cache-consumer-group",
            id = "unused-cache",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUnusedCacheTopic(String message) {
        System.out.println("🔵 [unused-cache-topic] Received: " + message);
        try {
            dataFeedService.resolveUnusedCacheRecord(extractUrl(message));
        } catch (Exception e) {
            System.err.println("🔵 [unused-cache-topic] Error: " + e.getMessage());
        }
    }

    @KafkaListener(
            topics = "resolve-url-topic",
            groupId = "resolve-url-group",
            id = "resolve-url",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeResolveUrlTopic(String message) {
        System.out.println("🟢 [resolve-url-topic] Received: " + message);
        try {
            Monarch monarch = dataFeedService.resolveUrlSimple(extractUrl(message));
            if (monarch != null) {
                Thread.sleep(15000);
            }
        } catch (Exception e) {
            System.err.println("🟢 [resolve-url-topic] Error: " + e.getMessage());
        }
    }

    private String extractUrl(String json) {
        JSONObject obj = new JSONObject(json);
        return obj.getString("url");
    }

//    private long getRemainingMessages(String topic, int partition) {
//        TopicPartition tp = new TopicPartition(topic, partition);
//
//        // latest offset
//        long endOffset = offsetConsumer.endOffsets(List.of(tp)).get(tp);
//
//        // current consumer offset
//        long currentOffset = registry
//                .getListenerContainer("resolve-url")
//                .getContainerProperties()
//                .getKafkaConsumer()
//                .position(tp);
//
//        return endOffset - currentOffset;
//    }
}