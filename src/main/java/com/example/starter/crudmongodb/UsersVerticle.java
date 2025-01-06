package com.example.starter.crudmongodb;

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

public class UsersVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        // Deploy necessary verticles
        vertx.deployVerticle(new DbVerticle(), dbDeployment -> {
            if (dbDeployment.succeeded()) {
                vertx.deployVerticle(new UsersServicesVerticle(), serviceDeployment -> {
                    if (serviceDeployment.succeeded()) {
                        setupHttpServer(startPromise);
                    } else {
                        startPromise.fail(serviceDeployment.cause());
                    }
                });
            } else {
                startPromise.fail(dbDeployment.cause());
            }
        });
    }

    private void setupHttpServer(Promise<Void> startPromise) {
        String pathToContract = "src/main/resources/api/openapi.yaml";

        OpenAPIContract.from(vertx, pathToContract)
            .onSuccess(contract -> {
                RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract, RequestExtractor.withBodyHandler());

                // CORS Configuration
                routerBuilder.rootHandler(CorsHandler.create()
                    .addOrigin("*")
                    .allowedMethod(HttpMethod.PUT)
                    .allowedMethod(HttpMethod.GET)
                    .allowedMethod(HttpMethod.POST)
                    .allowedMethod(HttpMethod.DELETE)
                    .allowedMethod(HttpMethod.OPTIONS)
                    .allowedHeader("Content-Type")
                    .allowCredentials(true)
                );

                // Body Handler
                routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(30 * 1024 * 1024));

                // Route Handlers
                routerBuilder.getRoute("createUser").addHandler(this::usersHandler);
                routerBuilder.getRoute("listUsers").addHandler(this::usersHandler);
                routerBuilder.getRoute("updateUser").addHandler(this::usersHandler);
                routerBuilder.getRoute("deleteUser").addHandler(this::usersHandler);

                Router router = routerBuilder.createRouter();

                vertx.createHttpServer().requestHandler(router).listen(8080)
                .onComplete(http -> {
                    if (http.succeeded()) {
                        startPromise.complete();
                        System.out.println("HTTP server started on port 8080");
                    } else {
                        startPromise.fail(http.cause());
                    }
                });
            })
            .onFailure(err -> {
                System.err.println("Failed to load OpenAPI contract: " + err.getMessage());
                startPromise.fail(err);
            });
    }


    private void usersHandler(RoutingContext ctx) {
      String path = ctx.request().path();
      JsonObject data = ctx.getBodyAsJson();
      String eventBusAddress;

      switch (path) {
          case "/users/create" -> eventBusAddress = "user.add";
          case "/users/list" -> {
              eventBusAddress = "user.list";
              data = new JsonObject().put("filter", data);
            }
          case "/users/update" -> eventBusAddress = "user.update";
          case "/users/delete" -> eventBusAddress = "user.delete";
          default -> {
              ctx.response()
                      .setStatusCode(400)
                      .putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("error", "Invalid route").encode());
              return;
            }
      }

      vertx.eventBus().request(eventBusAddress, data, reply -> {
          if (reply.succeeded()) {
              JsonObject result = (JsonObject) reply.result().body();
              ctx.response()
                  .setStatusCode(200)
                  .putHeader("Content-Type", "application/json")
                  .end(result.encode());
          } else {
              int statusCode = reply.cause().getMessage().contains("not found") ? 404 : 500;
              ctx.response()
                  .setStatusCode(statusCode)
                  .putHeader("Content-Type", "application/json")
                  .end(new JsonObject()
                      .put("error", reply.cause().getMessage())
                      .encode());
          }
      });
  }
}
