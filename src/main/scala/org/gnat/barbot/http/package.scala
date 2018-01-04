package org.gnat.barbot

package object http {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
  import spray.json.DefaultJsonProtocol

  sealed trait BarMaintenanceMessage

  case class RegisterMessage(name: String, locationUrl: String)
    extends BarMaintenanceMessage

  case class UnregisterMessage(id: Int) extends BarMaintenanceMessage

  case class UpdateMessage(id: Int, locationUrl: String)
    extends BarMaintenanceMessage

  trait ServerJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val registerFormat = jsonFormat2(RegisterMessage)
    implicit val unregisterFormat = jsonFormat1(UnregisterMessage)
    implicit val updateMessage = jsonFormat2(UpdateMessage)
  }

  sealed trait ExternalEventMessage

  case class BarStateMessage(name: String,
                                   location: String,
                                   openHours: String,
                                   beersList: List[String],
                                   wineList: List[String],
                                   cuisine: List[String])
    extends ExternalEventMessage

  trait ClientJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val barStateFormat = jsonFormat(BarStateMessage.apply,
      "name",
      "location",
      "openHours",
      "beer",
      "wine",
      "cuisine")
  }

  //  TODO possibly another way to do unmarshalling below?
  //  object ClientJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  //    implicit val barStateFormat = jsonFormat7(BarStateMessage)
  //  }

}
