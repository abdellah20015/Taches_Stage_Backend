package com.example.starter.websocket;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class ChatVerticle extends AbstractVerticle {

  @Override
  public void start() {
    Router router = Router.router(vertx);
    router.route("/*").handler(StaticHandler.create());

    HttpServerOptions serverOptions = new HttpServerOptions().setIdleTimeout(300000);
    HttpServer server = vertx.createHttpServer(serverOptions);

    server.webSocketHandler(this::handleWebSocket);

    server.requestHandler(router).listen(8080, result -> {
      if (result.succeeded()) {
        System.out.println("Server running on port 8080");
      } else {
        System.out.println("Server failed: " + result.cause().getMessage());
      }
    });
  }

  private void handleWebSocket(ServerWebSocket webSocket) {
    if (webSocket.path().equals("/chat")) {
      webSocket.textMessageHandler(message -> {
        vertx.eventBus().publish("chat.broadcast", message);
      });

      vertx.eventBus().consumer("chat.broadcast", message -> {
        webSocket.writeTextMessage(message.body().toString());
      });

      webSocket.closeHandler(v -> {
        System.out.println("WebSocket closed");
      });
    } else {
      webSocket.reject();
    }
  }
}
