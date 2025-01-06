package com.example.starter;

// import com.example.starter.crudserverhttp.UserVerticle;
// import com.example.starter.eventbus.ReceiverVerticle;
// import com.example.starter.eventbus.SenderVerticle;


// import com.example.starter.authentification.AuthVerticle;

import com.example.starter.serveroperation.ServerCrudAuth;

import io.vertx.core.AbstractVerticle;


import io.vertx.core.Promise;




public class MainVerticle extends AbstractVerticle {


  @Override
  public void start(Promise<Void> startPromise) throws Exception{



    // vertx.deployVerticle(new SenderVerticle() );
    // vertx.deployVerticle(new ReceiverVerticle());
    // vertx.deployVerticle(new UserVerticle());
    // vertx.deployVerticle(new AuthVerticle());
    // vertx.deployVerticle(new ChatVerticle());
    vertx.deployVerticle(new ServerCrudAuth());
    }







}

