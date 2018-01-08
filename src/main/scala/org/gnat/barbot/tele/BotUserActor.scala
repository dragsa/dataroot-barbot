package org.gnat.barbot.tele

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config


object BotUserActor {

  def props(userId: String)(implicit config: Config) = Props(new BotUserActor(userId))
}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userIr: String)(implicit config: Config) extends Actor with ActorLogging {

  override def preStart = {
    log.debug(s"actor ${self.path.name} is alive and well")
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
  }

  override def receive: Receive = {
    case a => log.debug(s"actor ${self.path.name} received message:\n $a")
  }
}
