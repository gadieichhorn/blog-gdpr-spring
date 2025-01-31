package com.rds.gdpr.patterns.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebServerVerticle extends AbstractVerticle {

    private HttpServer server;

    public void start(Promise future) {

        OpenAPI3RouterFactory.create(this.vertx, "webroot/private/swagger/chat.json", openAPI3RouterFactoryAsyncResult -> {

            if (openAPI3RouterFactoryAsyncResult.failed()) {
                Throwable exception = openAPI3RouterFactoryAsyncResult.cause();
                log.error("oops, something went wrong during factory initialization", exception);
                future.fail(exception);
            }

            OpenAPI3RouterFactory routerFactory = openAPI3RouterFactoryAsyncResult.result()
                    .mountServicesFromExtensions();

            routerFactory.addFailureHandlerByOperationId("createUser", routingContext -> {
                log.error("Exception", routingContext.failure());
            });

            Router router = routerFactory.setOptions(new RouterFactoryOptions()
                    .setMountResponseContentTypeHandler(true))
                    .getRouter();

            AuthProvider authProvider = ShiroAuth.create(vertx, ShiroAuthRealmType.PROPERTIES, new JsonObject());
            router.route().handler(BodyHandler.create());
            router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setAuthProvider(authProvider));

            AuthHandler redirectAuthHandler = RedirectAuthHandler.create(authProvider, "/login.html");
            router.route("/private/*").handler(redirectAuthHandler);
            router.post("/login").handler(FormLoginHandler.create(authProvider));
            router.route().handler(StaticHandler.create("webroot"));

            final ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create(vertx);

            router.get("/private/chat.html").handler(ctx -> {
                // we define a hardcoded title for our application
                JsonObject data = new JsonObject()
                        .put("welcome", ctx.user().principal().getValue("username"));

                // and now delegate to the engine to render it.
                engine.render(data, "templates/chat.html", res -> {
                    if (res.succeeded()) {
                        ctx.response().end(res.result());
                    } else {
                        log.warn("Failed to render page: {}", res.cause().getMessage());
                        ctx.fail(res.cause());
                    }
                });
            });

            router.mountSubRouter("/eventbus", SockJSHandler.create(vertx)
                    .bridge(new BridgeOptions()
                            .addInboundPermitted(new PermittedOptions()
//                                    .setRequiredAuthority("write-messages")
                                    .setAddress("chat-service-inbound"))
                            .addOutboundPermitted(new PermittedOptions()
//                                    .setRequiredAuthority("read-messages")
                                    .setAddress("chat-service-outbound"))));

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

    }

    public void stop() {
        this.server.close();
    }

}
