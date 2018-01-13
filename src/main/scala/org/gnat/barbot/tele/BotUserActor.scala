package org.gnat.barbot.tele

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.typesafe.config.Config
import info.mukel.telegrambot4s.models.Message
import org.gnat.barbot.Database
import org.gnat.barbot.http.BarStateMessage
import org.gnat.barbot.http.ClientCachingActor.{CachingActorCache, CachingActorProvideCache}
import org.gnat.barbot.tele.BotLexicon._
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

  case object StateDialog extends State

  case object StateDecision extends State

  // data
  sealed trait Data

  case object DataEmpty extends Data

  case class DataDialog(routineName: String,
                        routineStepsLeft: List[String],
                        dialogHistory: Map[String, String]) extends Data

  case class DataDecision(msg: Message, dialogResultWithWeights: Map[String, (String, Int)]) extends Data

  // outgoing events
  sealed trait Event

  case class EventQuestion(text: String)(implicit val msg: Message) extends Event

  case class EventQuestionEnd(text: String)(implicit val msg: Message) extends Event

  case class EventDecisionMade(text: String)(implicit val msg: Message) extends Event

  case class EventError(text: String)(implicit val msg: Message) extends Event

  case class EventReset(text: String)(implicit val msg: Message) extends Event

  def props(userId: String, cachingActor: ActorRef)(implicit config: Config, db: Database) = Props(new BotUserActor(userId, cachingActor))

  import scala.reflect.runtime.universe._
  val barFields = typeOf[BarStateMessage].members.collect {
    case m: MethodSymbol if m.isCaseAccessor => m
  }.toList.map(_.toString.drop("value ".length))
}

/* User actor which should:
    - hold the state of each user session
    - provide facilities to maintain FSM
    - all of the above include processing of commands and messages
    */

class BotUserActor(userId: String, cachingActor: ActorRef)(implicit config: Config, db: Database) extends FSM[State, Data] with ActorLogging {

  // TODO remove blocking code in favor of DB access failures
  // not that flexible, but avoiding separate threads in replies to parent is mandatory
  val actualFlows = Await.result(db.flowRepository.getAll, Duration.Inf)

  import scala.collection.JavaConverters._

  val priorities = config.getObject("bot.priorities").unwrapped().asScala.toMap.map { case (k, v) => (k, v.asInstanceOf[Int]) }

  startWith(StateIdle, DataEmpty)

  when(StateIdle) {
    case Event(RequestInitSuggestion(msg), DataEmpty) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying {
        EventQuestion((String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)
      }

    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateIdle) using DataEmpty replying EventReset(sessionRestartedInIdle + "\n\n" +
        String.format(sessionStarted, getUserFirstName).stripMargin)
  }

  when(StateDialog) {
    case Event(RequestInitSuggestion(msg), _) =>
      implicit val msgRef = msg
      stay replying EventError(String.format(decisionDialogAlreadyExists, getUserFirstName).stripMargin)

    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying EventReset(sessionRestartedInDialog + "\n\n" +
        (String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)

    case Event(RequestPayload(msg), DataEmpty) =>
      // first step in dialog - flow choice should be made
      // Option.get is safe here - no empty inputs are propagated due to DispatcherActor logic in front
      implicit val msgRef = msg
      actualFlows.find(_.name == msg.text.get) match {
        case Some(flow) =>
          val flowSteps = flow.steps.split(",").toList
          goto(StateDialog) using DataDialog(msg.text.get, flowSteps, Map()) replying
            EventQuestion(String.format(questions(flowSteps.headOption.getOrElse("default")), getUserFirstName))
        case None =>
          stay replying EventError(s"flow '${msg.text.get}' doesn't exists")
      }

    case Event(RequestPayload(msg), dd@DataDialog(flow, stepsLeft, history)) =>
      // TODO user input validation needed, EventError can be used here
      // "recursive" steps in dialog - all flow steps steps should be fulfilled
      // Option.get is safe here - no empty inputs are propagated due to DispatcherActor logic in front
      implicit val msgRef = msg
      if (stepsLeft.length == 1) {
        goto(StateDecision) using DataDecision(msg, (history + (stepsLeft.head -> msg.text.get)).map {
          case (k, v) => k -> (v, priorities(k))
        }) replying EventQuestionEnd(s"calculating, please be patient...")
      } else {
        goto(StateDialog) using dd.copy(flow, stepsLeft.tail, history + (stepsLeft.head -> msg.text.get)) replying
          EventQuestion(String.format(questions(stepsLeft.tail.head), getUserFirstName))
      }
  }

  when(StateDecision) {
    case Event(RequestResetSuggestion(msg), _) =>
      implicit val msgRef = msg
      goto(StateDialog) using DataEmpty replying EventReset(sessionRestartedInDialog + "\n\n" +
        (String.format(decisionDialogStarted, getUserFirstName) +
          (actualFlows.map(f => "|" + f.name + " -> " + f.description) mkString "\n")).stripMargin)

    case Event(RequestInitSuggestion(msg), _) =>
      implicit val msgRef = msg
      stay replying EventError(String.format(decisionDialogAlreadyExists, getUserFirstName).stripMargin)

    case Event(CachingActorCache(cache), DataDecision(msg, dialogHistory)) =>
      implicit val msgRef = msg
      log.info(s"got next cache from sibling:\n $cache")
      // TODO decision calculation here
      val barSortedByWeight = cache.map(barEvaluator(_, dialogHistory)).sortBy(_._1)
        .reverse.map(bar => s"score: ${bar._1}, bar: ${bar._2}, site: ${bar._3}")
      context.parent ! EventDecisionMade(barSortedByWeight mkString "\n")
      stay
  }

  private def barEvaluator(barState: BarStateMessage, dialogHistory: Map[String, (String, Int)]): (Double, String, String) = {
//    val barParametersFromUser = barFields.filter(dialogHistory.keys.toList.contains(_))
    val locationFromDialog = dialogHistory.getOrElse("location", ("default", 0))
    ((if (barState.location == locationFromDialog._1) locationFromDialog._2 else 0).toDouble, barState.name, barState.site)
  }

  private def weightFuncation(weightTuple: (String, Int)) = {
    weightTuple._1 match {
      case "default" => weightTuple._2.toDouble
      case "location" =>
      case "openHours" =>
      case "placesAvailable" =>
      case "cuisine" =>
      case "wine" =>
      case "beer" =>
    }
  }

  onTransition {
    case stateChange@(StateDialog -> StateDecision) =>
      log.info(s"I am ${self.path.name}:\n state is changing\n\t${stateChange._1 + " -> " + stateChange._2}\n current data is\n\t$stateData\n next data is\n\t$nextStateData")
      log.info(s"calculating possible variants")
      cachingActor ! CachingActorProvideCache

    case stateChange =>
      log.info(s"I am ${self.path.name}:\n state is changing\n\t${stateChange._1 + " -> " + stateChange._2}\n current data is $stateData\n next data is\n\t$nextStateData")
  }

  override def preStart = {
    log.debug(s"${self.path.name} is alive and well")
    super.preStart
  }

  override def postStop = {
    log.debug(s"${self.path.name} is dying")
    super.postStop
  }

  initialize
}
