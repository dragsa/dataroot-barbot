package org.gnat.barbot.http

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import spray.json._

object ClientHttpActor {

  sealed trait HttpActorRequest

  case class GetTarget(id: Int, url: String) extends HttpActorRequest

  case class GetLocation(url: String) extends HttpActorRequest

  def props(implicit config: Config) = Props(new ClientHttpActor)
}

class ClientHttpActor(implicit config: Config) extends Actor with ClientJsonSupport with ActorLogging {

  var barId: Option[Int] = None
  val cacheConfig = config.getConfig("cache")
  val banishToValhalla = cacheConfig.getBoolean("banish-to-valhalla")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  import akka.pattern.pipe
  import context.dispatcher

  override def receive: Receive = {

    case gt@GetTarget(id, url) =>
      barId = Option(id)
      log.info(s"actor ${self.path.name} received:\n $gt")
      http.singleRequest(HttpRequest(uri = url)).pipeTo(self)

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes
        .map(_.utf8String)
        .map { potentialEntity =>
          val parsedEntity = potentialEntity.parseJson
          log.debug(s"parsed next content:\n $parsedEntity")
          // TODO signal to parent that convertTo was not successful
          val unmarshallingEntity = parsedEntity.convertTo[BarStateMessage]
          log.debug(s"unmarshalling next entity:\n $unmarshallingEntity")
          unmarshallingEntity
        }
        .runForeach(bsm => {
          log.info(s"actor ${self.path.name} sending next BarState to parent:\n $bsm")
          context.parent ! (barId, bsm)
        })
      self ! PoisonPill

    case resp@HttpResponse(code, headers, entity, _) =>
      log.info(s"actor ${self.path.name} request failed, response code:\n $code")
      resp.discardEntityBytes()
      // TODO for simplicity all non-200OK responses are triggers for target becoming dead, may be improved
      // after this the only way for target to get back - update itself via public API
      // see barbot.cache.banish-to-valhalla for details
      log.info(s"actor ${self.path.name} sending BarDead to parent for target:\n ${barId.get}")
      context.parent ! BarDeadMessage(barId.get)
      self ! PoisonPill

    case Failure(msg) =>
      log.info(s"actor ${self.path.name} actor failure happened:\n $msg")
      // TODO for simplicity all Failures are triggers for target to be temporary excluded, may be improved
      // so far network issue is the only event identified leading to Failure
      // see barbot.cache.limbo-resurrection-timeout for details
      log.info(s"actor ${self.path.name} sending next BarExpired to parent for target:\n ${barId.get}")
      context.parent ! BarExpiredMessage(barId.get)
      self ! PoisonPill

    case um@_ =>
      log.info(s"${self.path.name} received unexpected message:\n $um")
      self ! PoisonPill
  }

  override def preStart = {
    log.debug(s"${self.path.name} is alive and well")
  }

  override def postStop = {
    log.debug(s"${self.path.name} is dying")
  }
}
