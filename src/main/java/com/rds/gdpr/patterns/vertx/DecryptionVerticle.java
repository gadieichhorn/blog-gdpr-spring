package com.rds.gdpr.patterns.vertx;

import com.rds.gdpr.patterns.dto.ChatMessageDto;
import com.rds.gdpr.patterns.model.ChatMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class DecryptionVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", "localhost:9092");
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("group.id", "chat");
        config.put("auto.offset.reset", "earliest");
        config.put("enable.auto.commit", "false");

        KafkaConsumer.create(vertx, config)
                .handler(record -> {
                    log.info("Processing key={} value={} ,partition={} ,offset={}", record.key(), record.value(), record.partition(), record.offset());
                    message(Json.decodeValue(record.value().toString(), ChatMessage.class), chatMessageDto -> {
                        vertx.eventBus().publish("chat-service-outbound", Json.encode(chatMessageDto));
                    });
                })
                .subscribe("chat-messages", startPromise);
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        stopPromise.complete();
    }

    private void message(ChatMessage message, Consumer<ChatMessageDto> handler) {
        ChatMessageDto.builder().from(message.getFrom()).message(message.getMessage()).build();
    }
}
