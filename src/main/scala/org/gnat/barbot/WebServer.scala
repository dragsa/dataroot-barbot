package org.gnat.barbot

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.io.StdIn

object WebServer extends App with ApiRouter with LazyLogging {

    implicit val system = ActorSystem("slick-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

    initDatabase

    logger.info("Started server, press enter to stop")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
