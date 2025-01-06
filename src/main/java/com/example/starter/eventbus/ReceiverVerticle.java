package com.example.starter.eventbus;

import io.vertx.core.AbstractVerticle;

public class ReceiverVerticle extends AbstractVerticle {
  @Override
  public void start() {
    vertx.eventBus().consumer("message.address", message -> {
      System.out.println("Receiver : Message recu -> " + message.body());
      message.reply("Reponse du ReceiverVerticle!");
    });
  }
}
