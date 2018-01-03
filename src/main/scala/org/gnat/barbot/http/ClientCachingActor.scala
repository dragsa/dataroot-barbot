package org.gnat.barbot.http

import akka.actor.{Actor, Cancellable, Props}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.gnat.barbot.Database
import org.gnat.barbot.http.ClientCachingActor.{CachingActorRefresh, CachingActorStart}
import org.gnat.barbot.http.ClientHttpActor.GetTarget

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ClientCachingActor {

  sealed trait CachingActorControlMessage

  case object CachingActorStart extends CachingActorControlMessage

  case object CachingActorRefresh extends CachingActorControlMessage

  def props(config: Config)(implicit db: Database) = Props(new ClientCachingActor(config))
}

class ClientCachingActor(config: Config)(implicit db: Database) extends Actor with LazyLogging {

  var refreshTimer: Option[Cancellable] = None
  var cachedTargets = Map[String, String]()

  override def receive: Receive = {
    case CachingActorStart =>
      logger.info("received Start message")
      refreshTimer.map(_.cancel)
      refreshTimer = Option(
        context.system.scheduler.schedule(0 seconds, config.getInt("cacheTimeout") seconds, self, CachingActorRefresh))
    case CachingActorRefresh =>
      logger.info("received Refresh message")
      db.barRepository.getAllActive.map(bars => bars.foreach(activeBar => context.actorOf(Props(new ClientHttpActor)) ! GetTarget(activeBar.infoSource)))
    //    case BarStateMessage(beersList) => ???
  }
}
