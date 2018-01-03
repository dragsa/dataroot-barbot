package org.gnat.barbot

package object http {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
  import spray.json.DefaultJsonProtocol

  sealed trait Registration

  final case class RegisterMessage(name: String, locationUrl: String) extends Registration

  final case class UnregisterMessage(id: Int) extends Registration

  trait ServerJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val registerFormat = jsonFormat2(RegisterMessage)
    implicit val unregisterFormat = jsonFormat1(UnregisterMessage)
  }

  sealed trait ExternalEventMessage

  final case class BarStateMessage(id: Int, name: String, location: String, openHours: String, beersList: List[String],
                                   wineList: List[String], cuisine: List[String]) extends ExternalEventMessage

  trait ClientJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val barStateFormat = jsonFormat(BarStateMessage.apply,
      "id",
      "name",
      "location",
      "openHours",
      "beer",
      "wine",
      "cuisine")
  }

}
