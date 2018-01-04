package org.gnat.barbot.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import org.gnat.barbot.models.Bar
import org.gnat.barbot.Database

import scala.util.{Failure, Success}

trait ServerApiRouter extends Database with ServerJsonSupport {

  implicit def registrationRejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, err) =>
          complete(StatusCodes.BadRequest, "wrong input data")
      }
      .result

  val route =
    pathSingleSlash {
      complete("root")
    } ~ pathPrefix("register") {
      pathEndOrSingleSlash {
        post {
          handleRejections(registrationRejectionHandler) {
            entity(as[RegisterMessage]) { register =>
              logger.info("Register Message received: " + register)
              onComplete(barRepository.createOne(Bar(register.name, register.locationUrl))) {
                case Success(bar) => complete(bar.id.get.toString)
                case Failure(msg) => complete(StatusCodes.BadRequest, msg.getMessage)
              }
            }
          }
        }
      }
    } ~ pathPrefix("unregister") {
      pathEndOrSingleSlash {
        post {
          handleRejections(registrationRejectionHandler) {
            entity(as[UnregisterMessage]) { unregister =>
              logger.info("Unregister Message received: " + unregister)
              onSuccess(barRepository.getOneById(unregister.id)) {
                case Some(bar) =>
                  onSuccess(barRepository.updateOne(bar.copy(isActive = false))) {
                    case 1 =>
                      logger.info(bar + " was removed from active bar list")
                      complete(bar.name + " was removed from active bar list")
                    case 0 => complete(StatusCodes.BadRequest)
                  }
                case None => complete(StatusCodes.NotFound)
              }
            }
          }
        }
      }
    } ~ pathPrefix("update") {
      pathEndOrSingleSlash {
        post {
          handleRejections(registrationRejectionHandler) {
            entity(as[UpdateMessage]) { update =>
              logger.info("Update Message received: " + update)
              onSuccess(barRepository.getOneById(update.id)) {
                case Some(bar) =>
                  onSuccess(barRepository.updateOne(bar.copy(infoSource = update.locationUrl))) {
                    case 1 =>
                      logger.info(bar.name + " was updated")
                      complete(bar.name + " was updated")
                    case 0 => complete(StatusCodes.BadRequest)
                  }
                case None => complete(StatusCodes.NotFound)
              }
            }
          }
        }
      }
    }
}
