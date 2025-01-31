package com.rds.gdpr.patterns.vertx;

import com.github.javafaker.Faker;
import com.rds.gdpr.patterns.AbstractMongoTest;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class UsersVerticleTest extends AbstractMongoTest {

    private final Faker faker = new Faker();

    @BeforeEach
    void beforeEach(Vertx vertx, VertxTestContext testContext) {
        JsonObject mogno = new JsonObject().put("mongo", mongoConfig);
        vertx.deployVerticle(new UsersVerticle(), new DeploymentOptions().setConfig(mogno),
                testContext.succeeding(id -> testContext.completeNow()));
    }

    @Test
    void deployed(VertxTestContext testContext) throws Throwable {
        testContext.completeNow();
    }

}