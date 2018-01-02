package org.gnat.barbot.models

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.Future

//case class Bar(name: String,
//               infoSource: String,
//               isActive: Boolean,
//               id: Int)

class BarTable(tag: Tag) extends Table[Bar](tag, "bars") {
  def name = column[String]("name", O.Unique, O.Length(100))

  def infoSource = column[String]("info_source")

  def isActive = column[Boolean]("is_active")

  def id = column[Int]("id", O.PrimaryKey, O.Unique, O.AutoInc)

  def * =
    (name, infoSource, isActive, id.?) <> (Bar.apply _ tupled, Bar.unapply)

}

object BarTable {
  val table = TableQuery[BarTable]
}

class BarRepository(implicit db: Database) {
  val barTableQuery = BarTable.table

  def createOne(bar: Bar): Future[Bar] = {
    db.run(barTableQuery returning barTableQuery += bar)
  }

  def createMany(bars: List[Bar]): Future[Seq[Bar]] = {
    db.run(barTableQuery returning barTableQuery ++= bars)
  }

  def updateOne(bar: Bar): Future[Int] = {
    db.run(
      barTableQuery
        .filter(_.id === bar.id)
        .update(bar))
  }

  def getOneById(id: Int): Future[Option[Bar]] = {
    db.run(barTableQuery.filter(_.id === id).result.headOption)
  }

  def getAll: Future[Seq[Bar]] = {
    db.run(barTableQuery.result)
  }

  def deleteOneById(id: Int): Future[Int] = {
    db.run(barTableQuery.filter(_.id === id).delete)
  }
}
