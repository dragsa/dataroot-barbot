package org.gnat.barbot

import com.typesafe.scalalogging.LazyLogging
import org.gnat.barbot.models._
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait Database extends LazyLogging {
  implicit val db = Database.forConfig("barbot.db")
  implicit val userRepository = new UserRepository
  implicit val barRepository = new BarRepository
  implicit val visitRepository = new VisitRepository
  implicit val flowRepository = new FlowRepository

  private val tables = Map("users" -> userRepository.userTableQuery,
    "bars" -> barRepository.barTableQuery,
    "visits" -> visitRepository.visitTableQuery,
    "flows" -> flowRepository.flowTableQuery)

  // TODO move flows into configuration file, sort of complex task
  private val defaultFlows = List(Flow("basic", "location,openHours", "short, fast but less accurate"),
    Flow("full", "location,openHours,beer,wine,cuisine", "painful, slow but precise"))

  // TODO add schema checking and migration code
  private def initTables: Unit = {
    tables.keys.foreach(tableCreator)
  }

  private def initFlows: Unit = {
    defaultFlows.foreach(flowToCreate => Await.result(flowRepository.getOneByName(flowToCreate.name).flatMap {
      case None =>
        logger.info("creating flow " + flowToCreate.name)
        flowRepository.createOne(flowToCreate)
      case Some(_) =>
        Future.successful()
    }, Duration.Inf))
  }

  private def tableCreator(tableName: String): Unit = {
    Await.result(
      db.run(MTable.getTables(tableName))
        .flatMap(matchedTables =>
          if (matchedTables.isEmpty) {
            logger.info(tableName + " table doesn't exist, creating...")
            db.run(tables(tableName).schema.create)
          } else Future.successful())
        .andThen { case _ => logger.info(tableName + " table check finished") },
      Duration.Inf
    )
  }

  def initDatabase {
    logger.info("initialization started")
    initTables
    initFlows
  }
}
