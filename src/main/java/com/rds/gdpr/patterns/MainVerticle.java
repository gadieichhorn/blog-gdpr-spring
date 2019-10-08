package com.rds.gdpr.patterns;

import com.rds.gdpr.patterns.service.UsersService;
import com.rds.gdpr.patterns.service.UsersServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.serviceproxy.ServiceBinder;
import lombok.extern.slf4j.Slf4j;

import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class MainVerticle extends AbstractVerticle {

    private HttpServer server;
    private ServiceBinder serviceBinder;
    private MessageConsumer<JsonObject> consumer;

    public void start(Promise future) {

        serviceBinder = new ServiceBinder(this.vertx);

        consumer = serviceBinder
                .setAddress("users.proxy")
                .register(UsersService.class, new UsersServiceImpl(vertx));

        OpenAPI3RouterFactory.create(this.vertx, "chat.json", openAPI3RouterFactoryAsyncResult -> {

            if (openAPI3RouterFactoryAsyncResult.failed()) {
                // Something went wrong during router factory initialization
                Throwable exception = openAPI3RouterFactoryAsyncResult.cause();
                log.error("oops, something went wrong during factory initialization", exception);
                future.fail(exception);
            }

            OpenAPI3RouterFactory routerFactory = openAPI3RouterFactoryAsyncResult.result()
                    .mountServicesFromExtensions();

            Router router = routerFactory.setOptions(new RouterFactoryOptions()
                    .setMountResponseContentTypeHandler(true))
                    .getRouter();


            router.mountSubRouter("/eventbus", SockJSHandler.create(vertx)
                    .bridge(new BridgeOptions()
                            .addInboundPermitted(new PermittedOptions()
                                    .setAddress("chat-service-inbound"))
                            .addOutboundPermitted(new PermittedOptions()
                                    .setAddress("chat-service-outbound"))));

            router.route().handler(StaticHandler.create("webroot"));

            vertx.eventBus().consumer("chat-service-inbound").handler(message -> {
                log.info("Chat: {}", message.body());

                String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));
                vertx.eventBus().publish("chat-service-outbound", timestamp + ": " + message.body());
            });

            server = vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"))
                    .requestHandler(router).listen((ar) -> {
                        if (ar.succeeded()) {
                            log.info("Server started on port {}", ar.result().actualPort());
                            future.complete();
                        } else {
                            log.error("oops, something went wrong during server initialization", ar.cause());
                            future.fail(ar.cause());
                        }
                    });
        });

        vertx.periodicStream(5000).handler(aLong ->
                vertx.eventBus().publish("chat-service-inbound", Json.encode(ChatMessage.builder()
                        .key(UUID.randomUUID())
                        .message(UUID.randomUUID().toString())
                        .build())));

    }

    public void stop() {
        this.server.close();
    }

}
