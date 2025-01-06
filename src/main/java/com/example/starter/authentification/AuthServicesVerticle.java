package com.example.starter.authentification;

import com.example.starter.config.MongoConfig;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.auth.mongo.MongoAuthorizationOptions;
import io.vertx.ext.auth.mongo.MongoUserUtil;
import io.vertx.ext.mongo.MongoClient;

public class AuthServicesVerticle extends AbstractVerticle {

    private MongoClient mongoClient;
    private MongoUserUtil userUtil;
    private MongoAuth authProvider;

    @Override
    public void start(Promise<Void> startPromise) {
        mongoClient = MongoConfig.createMongoClient(vertx);

        MongoAuthenticationOptions authOptions = new MongoAuthenticationOptions();
        MongoAuthorizationOptions authzOptions = new MongoAuthorizationOptions();

        authProvider = MongoAuth.create(mongoClient, new JsonObject());
        userUtil = MongoUserUtil.create(mongoClient, authOptions, authzOptions);

        vertx.eventBus().consumer("auth.signup", this::signup);
        // vertx.eventBus().consumer("auth.login", this::login);
        // vertx.eventBus().consumer("auth.logout", this::logout);

        startPromise.complete();
    }

    private void signup(Message<JsonObject> message) {
      JsonObject userData = message.body();
      String username = userData.getString("username");
      String password = userData.getString("password");

      if (username == null || password == null) {
          message.fail(-1, "Username and password are required");
          return;
      }

      JsonObject query = new JsonObject().put("username", username);
      mongoClient.findOne("user", query, null, findRes -> {
          if (findRes.succeeded()) {
              if (findRes.result() != null) {
                  message.fail(-1, "User already exists");
                  return;
              }

              userUtil.createUser(username, password, createRes -> {
                  if (createRes.succeeded()) {
                      message.reply(new JsonObject().put("message", "User registered successfully"));
                  } else {
                      message.fail(-1, "Failed to create user: " + createRes.cause().getMessage());
                  }
              });
          } else {

              message.fail(-1, "Error checking user existence: " + findRes.cause().getMessage());
          }
      });
  }

    // private void login(Message<JsonObject> message) {
    //     JsonObject credentials = message.body();
    //     String username = credentials.getString("username");
    //     String password = credentials.getString("password");

    //     if (username == null || password == null) {
    //         message.fail(-1, "Username and password are required");
    //         return;
    //     }

    //     JsonObject authInfo = new JsonObject()
    //         .put("username", username)
    //         .put("password", password);

    //     authProvider.authenticate(authInfo, authRes -> {
    //         if (authRes.succeeded()) {
    //             message.reply(new JsonObject().put("message", "Login successful"));

    //         } else {
    //             message.fail(401, "Invalid username or password");
    //         }
    //     });
    // }

    // private void logout(Message<JsonObject> message) {
    //     JsonObject userData = message.body();
    //     String username = userData.getString("username");

    //     if (username == null) {
    //         message.fail(-1, "Username is required for logout");
    //         return;
    //     }

    //     message.reply(new JsonObject().put("message", "User logged out successfully"));
    // }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (mongoClient != null) {
            mongoClient.close();
        }
        stopPromise.complete();
    }
}
