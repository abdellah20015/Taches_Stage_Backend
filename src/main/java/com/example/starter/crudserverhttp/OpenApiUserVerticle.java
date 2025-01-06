package com.example.starter.crudserverhttp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public class OpenApiUserVerticle extends AbstractVerticle {
  private final List<JsonObject> users = new ArrayList<>();

  @Override
  public void start(Promise<Void> startPromise) {
    String pathToContract = "src/main/resources/api/openapi.yaml";

    OpenAPIContract.from(vertx, pathToContract)
      .onSuccess(contract -> {
        RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract, RequestExtractor.withBodyHandler());
        routerBuilder.rootHandler(CorsHandler.create()
         .addOrigin("*")
         .allowedMethod(HttpMethod.PUT)
         .allowedMethod(HttpMethod.GET)
         .allowedMethod(HttpMethod.POST)
         .allowedMethod(HttpMethod.DELETE)
         .allowedMethod(HttpMethod.OPTIONS)
         .allowedHeader("Content-Type")
         .allowedHeader("Authorization")
         .allowCredentials(true)
         );
         routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(30 * 1024 * 1024));

         routerBuilder.getRoute("addUsers").addHandler(this::handleAddUser);
         routerBuilder.getRoute("getUsers").addHandler(this::handleGetUsers);
         routerBuilder.getRoute("updateUser").addHandler(this::handleUpdateUser);
         routerBuilder.getRoute("deleteUser").addHandler(this::handleDeleteUser);


         Router router = routerBuilder.createRouter();

         vertx.createHttpServer().requestHandler(router).listen(8080)
        .onComplete(http ->{
          if (http.succeeded()) {
            startPromise.complete();
            System.out.println("HTTP server started on port 8080");
          }
          else{
            startPromise.fail(http.cause());
          }
        });

      }).onFailure(err ->{
        System.err.println("Failed to load OpenAPI contract: " + err.getMessage());



      });
  }

  public void handleAddUser(RoutingContext ctx) {
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
}

  public void handleGetUsers(RoutingContext ctx) {
    ctx.response()
               .putHeader("content-type", "application/json")
               .end(users.toString());
  }

  public void handleUpdateUser(RoutingContext ctx) {
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
  }

  public void handleDeleteUser(RoutingContext ctx) {
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
  }


}
