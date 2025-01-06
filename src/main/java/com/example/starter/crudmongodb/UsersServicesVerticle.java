package com.example.starter.crudmongodb;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UsersServicesVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        vertx.eventBus().consumer("user.add", this::addUser);
        vertx.eventBus().consumer("user.get", this::getUser);
        vertx.eventBus().consumer("user.list", this::listUsers);
        vertx.eventBus().consumer("user.update", this::updateUser);
        vertx.eventBus().consumer("user.delete", this::deleteUser);

        startPromise.complete();
    }

    private void addUser(Message<JsonObject> message) {
        JsonObject user = message.body();
        if (!user.containsKey("name") || !user.containsKey("email")) {
            message.fail(-1, "Name and email are required");
            return;
        }


        JsonObject dbPayload = new JsonObject()
            .put("collection", "users")
            .put("document", user);


        vertx.eventBus().request("db.insert", dbPayload, dbReply -> {
            if (dbReply.succeeded()) {
                JsonObject result = (JsonObject) dbReply.result().body();
                JsonObject response = new JsonObject()
                    .put("id", result.getString("id"))
                    .put("name", user.getString("name"))
                    .put("email", user.getString("email"))
                    .put("message", "User added successfully");
                message.reply(response);
            } else {
                message.fail(-1, dbReply.cause().getMessage());
            }
        });
    }

    private void getUser(Message<JsonObject> message) {
        String userId = message.body().getString("id");

        if (userId == null) {
            message.fail(-1, "User ID is required");
            return;
        }

        JsonObject dbPayload = new JsonObject()
            .put("collection", "users")
            .put("query", new JsonObject().put("_id", userId));

        vertx.eventBus().request("db.find", dbPayload, dbReply -> {
            if (dbReply.succeeded()) {
                JsonArray results = (JsonArray) dbReply.result().body();
                if (!results.isEmpty()) {
                    message.reply(results.getJsonObject(0));
                } else {
                    message.fail(404, "User not found");
                }
            } else {
                message.fail(-1, dbReply.cause().getMessage());
            }
        });
    }

    private void listUsers(Message<JsonObject> message) {
        JsonObject filter = message.body().getJsonObject("filter", new JsonObject());

        JsonObject dbPayload = new JsonObject()
            .put("collection", "users")
            .put("query", filter);

        vertx.eventBus().request("db.find", dbPayload, dbReply -> {
            if (dbReply.succeeded()) {
                JsonArray users = (JsonArray) dbReply.result().body();
                message.reply(new JsonObject().put("users", users));
            } else {
                message.fail(-1, dbReply.cause().getMessage());
            }
        });
    }

    private void updateUser(Message<JsonObject> message) {
        JsonObject updateData = message.body();
        String userId = updateData.getString("id");

        if (userId == null) {
            message.fail(-1, "User ID is required");
            return;
        }

        // Remove 'id' from update data
        updateData.remove("id");

        JsonObject dbPayload = new JsonObject()
            .put("collection", "users")
            .put("id", userId)
            .put("update", updateData);

        vertx.eventBus().request("db.update", dbPayload, dbReply -> {
            if (dbReply.succeeded()) {
                message.reply(new JsonObject()
                    .put("id", userId)
                    .put("message", "User updated successfully"));
            } else {
                message.fail(404, dbReply.cause().getMessage());
            }
        });
    }

    private void deleteUser(Message<JsonObject> message) {
        String userId = message.body().getString("id");

        if (userId == null) {
            message.fail(-1, "User ID is required");
            return;
        }

        JsonObject dbPayload = new JsonObject()
            .put("collection", "users")
            .put("id", userId);

        vertx.eventBus().request("db.delete", dbPayload, dbReply -> {
            if (dbReply.succeeded()) {
                message.reply(new JsonObject()
                    .put("id", userId)
                    .put("message", "User deleted successfully"));
            } else {
                message.fail(404, dbReply.cause().getMessage());
            }
        });
    }
}
