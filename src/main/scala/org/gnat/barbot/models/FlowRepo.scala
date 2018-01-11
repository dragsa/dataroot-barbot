package org.gnat.barbot.models

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.Future

//case class Flow(name: String,
//                steps: String,
//                descriptions: String,
//                id: Int)

class FlowTable(tag: Tag) extends Table[Flow](tag, "flows") {
  def name = column[String]("name", O.Unique, O.Length(100))

  def steps = column[String]("steps")

  def description = column[String]("description")

  def id = column[Int]("id", O.PrimaryKey, O.Unique, O.AutoInc)

  def * =
    (name, steps, description, id.?) <> (Flow.apply _ tupled, Flow.unapply)

}

object FlowTable {
  val table = TableQuery[FlowTable]
}

class FlowRepository(implicit db: Database) {
  val flowTableQuery = FlowTable.table

  def createOne(flow: Flow): Future[Flow] = {
    db.run(flowTableQuery returning flowTableQuery += flow)
  }

  def createMany(flows: List[Flow]): Future[Seq[Flow]] = {
    db.run(flowTableQuery returning flowTableQuery ++= flows)
  }

  def updateOne(flow: Flow): Future[Int] = {
    db.run(
      flowTableQuery
        .filter(_.id === flow.id)
        .update(flow))
  }

  def getOneById(id: Int): Future[Option[Flow]] = {
    db.run(flowTableQuery.filter(_.id === id).result.headOption)
  }

  def getOneByName(name: String): Future[Option[Flow]] = {
    db.run(flowTableQuery.filter(_.name === name).result.headOption)
  }

  def getAll: Future[Seq[Flow]] = {
    db.run(flowTableQuery.result)
  }
}

