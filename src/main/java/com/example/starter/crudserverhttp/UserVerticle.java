package com.example.starter.crudserverhttp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class UserVerticle extends AbstractVerticle {
    private final List<JsonObject> users = new ArrayList<>();

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Gestion des CORS
        router.route().handler(ctx -> {
            ctx.response()
               .putHeader("Access-Control-Allow-Origin", "*")
               .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
               .putHeader("Access-Control-Allow-Headers", "content-type");
            ctx.next();
        });


        router.options().handler(ctx -> {
            ctx.response()
               .putHeader("Access-Control-Allow-Origin", "*")
               .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
               .putHeader("Access-Control-Allow-Headers", "content-type")
               .setStatusCode(204)
               .end();
        });

        // Route POST (Create)
        router.post("/users").handler(ctx -> {
            ctx.getBodyAsJsonArray().forEach(user -> {
                if (user instanceof JsonObject) {
                    JsonObject jsonUser = (JsonObject) user;
                    jsonUser.put("id", UUID.randomUUID().toString());
                    users.add(jsonUser);
                }
            });
            ctx.response()
               .putHeader("content-type", "application/json")
               .end(new JsonObject().put("message", "Utilisateurs ajoutés!").encode());
        });

        // Route GET (Read)
        router.get("/users").handler(ctx -> {
            ctx.response()
               .putHeader("content-type", "application/json")
               .end(users.toString());
        });

        // Route PUT (Update)
        router.put("/users/:id").handler(ctx -> {
            String userId = ctx.pathParam("id");
            JsonObject updatedUser = ctx.getBodyAsJson();
            boolean updated = false;

            for (int i = 0; i < users.size(); i++) {
                JsonObject user = users.get(i);
                if (user.getString("id").equals(userId)) {
                    updatedUser.put("id", userId);
                    users.set(i, updatedUser);
                    updated = true;
                    break;
                }
            }

            if (updated) {
                ctx.response()
                   .putHeader("content-type", "application/json")
                   .end(new JsonObject().put("message", "Utilisateur mis à jour!").encode());
            } else {
                ctx.response()
                   .setStatusCode(404)
                   .putHeader("content-type", "application/json")
                   .end(new JsonObject().put("message", "Utilisateur non trouvé!").encode());
            }
        });

        // Route DELETE (Delete)
        router.delete("/users/:id").handler(ctx -> {
            String userId = ctx.pathParam("id");
            boolean removed = users.removeIf(user -> user.getString("id").equals(userId));

            if (removed) {
                ctx.response()
                   .putHeader("content-type", "application/json")
                   .end(new JsonObject().put("message", "Utilisateur supprimé").encode());
            } else {
                ctx.response()
                   .setStatusCode(404)
                   .putHeader("content-type", "application/json")
                   .end(new JsonObject().put("message", "Utilisateur non trouvé!").encode());
            }
        });

        // Démarrage du serveur
        vertx.createHttpServer()
             .requestHandler(router)
             .listen(8080)
             .onSuccess(server -> {
                 System.out.println("Serveur demarre sur le port " + server.actualPort());
                 startPromise.complete();
             })
             .onFailure(startPromise::fail);
    }
}
