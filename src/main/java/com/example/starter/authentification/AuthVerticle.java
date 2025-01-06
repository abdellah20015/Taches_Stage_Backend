package com.example.starter.authentification;

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

public class AuthVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {

        vertx.deployVerticle(new AuthServicesVerticle(), serviceDeployment -> {
            if (serviceDeployment.succeeded()) {
                setupHttpServer(startPromise);
            } else {
                startPromise.fail(serviceDeployment.cause());
            }
        });
    }

    private void setupHttpServer(Promise<Void> startPromise) {
        String pathToContract = "src/main/resources/api/openapi.yaml";

        OpenAPIContract.from(vertx, pathToContract)
            .onSuccess(contract -> {
                RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract, RequestExtractor.withBodyHandler());

                routerBuilder.rootHandler(CorsHandler.create()
                    .addOrigin("*")
                    .allowedMethod(HttpMethod.POST)
                    .allowedMethod(HttpMethod.OPTIONS)
                    .allowedHeader("Content-Type")
                    .allowCredentials(true)
                );

                routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(30 * 1024 * 1024));

                routerBuilder.getRoute("userLogin").addHandler(this::authHandler);
                routerBuilder.getRoute("userSignup").addHandler(this::authHandler);
                routerBuilder.getRoute("userLogout").addHandler(this::authHandler);

                Router router = routerBuilder.createRouter();

                vertx.createHttpServer().requestHandler(router).listen(8081)
                .onComplete(http -> {
                    if (http.succeeded()) {
                        startPromise.complete();
                        System.out.println("Authentication HTTP server started on port 8081");
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

    private void authHandler(RoutingContext ctx) {
        String path = ctx.request().path();
        JsonObject data = ctx.getBodyAsJson();
        String eventBusAddress;

        switch (path) {
            case "/login" -> eventBusAddress = "auth.login";
            case "/signup" -> eventBusAddress = "auth.signup";
            case "/logout" -> eventBusAddress = "auth.logout";
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
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(reply.result().body().toString());
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", reply.cause().getMessage()).encode());
            }
        });
    }
}
