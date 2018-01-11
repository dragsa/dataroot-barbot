package org.gnat.barbot.tele

import akka.actor.{ActorLogging, FSM, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.models.Message
import org.gnat.barbot.Database
import org.gnat.barbot.tele.BotAlphabet._
import org.gnat.barbot.tele.BotUserActor._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object BotUserActor {

  // incoming requests
  sealed trait Request

  case class RequestInitSuggestion(msg: Message) extends Request

  case class RequestResetSuggestion(msg: Message) extends Request

  case class RequestPayload(msg: Message) extends Request

  // states
  sealed trait State

  case object StateIdle extends State

  case object StateDecision extends State

  // data
  sealed trait Data

  case object DataEmpty extends Data

  case class DataDialog(routineName: String, routineStepsLeft: List[String], dialogHistory: List[Message]) extends Data

  // outgoing events
  sealed trait Event

  case class EventQuestion(text: String)(implicit val msg: Message) extends Event

  case class EventError(text: String)(implicit val msg: Message) extends Event

  case class EventQuestionEnd(text: String)(implicit val msg: Message) extends Event

  case class EventReset(text: String)(implicit val msg: Message) extends Event

  def props(userId: String)(implicit config: Config, db: Database) = Props(new BotUserActor(userId))
}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userId: String)(implicit config: Config, db: Database) extends FSM[State, Data] with ActorLogging {

  // TODO removing blocking code
  // not that flexible, but avoiding separate threads in replies to parent is mandatory
  val actualFlows = Await.result(db.flowRepository.getAll, Duration.Inf)

  startWith(StateIdle, DataEmpty)

  when(StateIdle) {
    case Event(RequestInitSuggestion(msg), DataEmpty) =>
      implicit val msgRef = msg
      goto(StateDecision) using DataEmpty replying {
        EventQuestion((String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)
      }
  }

  when(StateDecision) {
    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateIdle) using DataEmpty replying EventReset("reset text")
    case Event(RequestPayload(msg), DataEmpty) =>
      implicit val msgRef = msg
      // first step in dialog - flow choice should be made
      if (actualFlows.map(_.name).contains(msg.text.get)) {
        val flow = actualFlows.find(_.name == msg.text.get)
        goto(StateDecision) using DataDialog(msg.text.get, flow.get.steps.split(",").toList, List()) replying EventQuestion(s"good choice!")
      }
      else
        stay replying EventError(s"flow '${msg.text.get}' doesn't exists")
//    case Event(RequestPayload(msg), DataDialog(flow, stepLeft, history)) =>
//      implicit val msgRef = msg
//      if
  }

  onTransition {
    //    case stateChange@(StateIdle -> StateDecision) =>
    //      log.info(s"I am ${self.path.name} and my state is changing ${stateChange._1 -> stateChange._2}")
    case stateChange =>
      log.info(s"I am ${self.path.name}:\n state is changing ${stateChange._1 + " -> " + stateChange._2}\n current data is $stateData\n next data is $nextStateData")
  }

  override def preStart = {
    log.debug(s"actor ${self.path.name} is alive and well")
    super.preStart
  }

  override def postStop = {
    log.debug(s"actor ${self.path.name} is dying")
    super.postStop
  }

  //  override def receive: Receive = {
  //    case a => log.debug(s"actor ${self.path.name} received message:\n $a")
  //      super.receive
  //  }
  initialize()
}
