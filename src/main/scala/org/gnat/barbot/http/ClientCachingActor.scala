package org.gnat.barbot.http

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.typesafe.config.Config
import org.gnat.barbot.Database
import org.gnat.barbot.http.ClientCachingActor.{CachingActorProvideCache, CachingActorRefreshCache, CachingActorStart}
import org.gnat.barbot.http.ClientHttpActor.GetTarget
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ClientCachingActor {

  sealed trait CachingActorControlMessage

  case object CachingActorStart extends CachingActorControlMessage

  case object CachingActorRefreshCache extends CachingActorControlMessage

  case object CachingActorProvideCache extends CachingActorControlMessage

  def props(config: Config)(implicit db: Database) = Props(new ClientCachingActor(config))
}

class ClientCachingActor(config: Config)(implicit db: Database) extends Actor with ActorLogging {

  var refreshTimer: Option[Cancellable] = None
  var cachedTargets: Map[String, String] = Map[String, String]()
  var senderRef: Option[ActorRef] = None
  val cacheTimeout = config.getInt("cacheTimeout")


  override def receive: Receive = {
    case CachingActorStart =>
      log.info("received Start message")
      refreshTimer.foreach(_.cancel)
      refreshTimer = Option(
        context.system.scheduler.schedule(0 seconds, cacheTimeout seconds, self, CachingActorRefreshCache))
    case CachingActorRefreshCache =>
      log.info("received Refresh message")
      db.barRepository.getAllActive.map(bars => bars.foreach(activeBar => context.actorOf(Props(new ClientHttpActor)) ! GetTarget(activeBar.infoSource)))
    case CachingActorProvideCache =>
      senderRef = Option(sender)
      senderRef.foreach(_ ! cachedTargets)
    //    case BarStateMessage(beersList) => ???
  }

  private def updateTargetsCache = ???
}
