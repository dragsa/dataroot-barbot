package org.gnat.barbot

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

final case class RegisterMessage(name: String, locationUrl: String)
final case class UnregisterMessage(id: Int)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val registerFormat = jsonFormat2(RegisterMessage)
    implicit val unregisterFormat = jsonFormat1(UnregisterMessage)
}
