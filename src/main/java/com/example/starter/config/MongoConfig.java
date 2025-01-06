package com.example.starter.config;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.core.Vertx;

public class MongoConfig {
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DB_NAME = "users_db";

    public static MongoClient createMongoClient(Vertx vertx) {
        JsonObject config = new JsonObject()
            .put("connection_string", CONNECTION_STRING)
            .put("db_name", DB_NAME);

        return MongoClient.createShared(vertx, config);
    }

    public static JsonObject getConfig() {
        return new JsonObject()
            .put("connection_string", CONNECTION_STRING)
            .put("db_name", DB_NAME);
    }
}
