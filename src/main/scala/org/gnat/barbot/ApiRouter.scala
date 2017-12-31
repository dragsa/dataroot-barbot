package org.gnat.barbot

import akka.http.scaladsl.server.Directives._

trait ApiRouter extends Database{

  val route =
    pathSingleSlash {
      complete("root")
    }
}
