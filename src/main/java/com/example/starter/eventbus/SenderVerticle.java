package com.example.starter.eventbus;

import io.vertx.core.AbstractVerticle;

public class SenderVerticle extends AbstractVerticle {
  @Override
  public void start() {
    String message = "Hello from SenderVerticle!";
    vertx.eventBus().request("message.address", message, reply -> {
      if (reply.succeeded()) {
        System.out.println("Reponse recue : " + reply.result().body());
      } else {
        System.err.println("Erreur lors de l'envoi du message : " + reply.cause().getMessage());
      }
    });
    System.out.println("Message envoye : " + message);
  }
}
