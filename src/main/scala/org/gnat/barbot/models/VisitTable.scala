package org.gnat.barbot.models

import java.sql.Timestamp

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.Future

//  case class Visit(user: Int,
//                   place: Int,
//                   mark: Int,
//                   date: Timestamp,
//                   id: Int)

class VisitTable(tag: Tag) extends Table[Visit](tag, "visits") {
  def user = column[Int]("user")

  def place = column[Int]("place")

  def mark = column[Int]("mark")

  def date = column[Timestamp]("date")

  def id = column[Int]("id", O.PrimaryKey, O.Unique, O.AutoInc)

  def userFk =
    foreignKey("user_fk_ID", user, TableQuery[UserTable])(_.id)

  def placeFk =
    foreignKey("place_fk_ID", place, TableQuery[BarTable])(_.id)

  def * =
    (user, place, mark, date, id) <> (Visit.apply _ tupled, Visit.unapply)

}

object VisitTable {
  val table = TableQuery[VisitTable]
}

class VisitRepository(implicit db: Database) {
  val visitTableQuery = VisitTable.table
  val userTableQuery = UserTable.table
  val barTableQuery = BarTable.table

  def createOne(visit: Visit): Future[Visit] = {
    db.run(visitTableQuery returning visitTableQuery += visit)
  }

  def createMany(visits: List[Visit]): Future[Seq[Visit]] = {
    db.run(visitTableQuery returning visitTableQuery ++= visits)
  }

  def updateOne(visit: Visit): Future[Int] = {
    db.run(
      visitTableQuery
        .filter(_.id === visit.id)
        .update(visit))
  }

  def getOneById(id: Int): Future[Option[Visit]] = {
    db.run(visitTableQuery.filter(_.id === id).result.headOption)
  }

  def getAllByUserId(id: Int): Future[Seq[Visit]] = {
    db.run(
      visitTableQuery
        .filter(visit => visit.user === id)
        .sortBy(_.date)
        .result)
  }

  def getAllByUserNickName(nickName: String): Future[Seq[Visit]] = {
    val query =
      (for {
        u <- userTableQuery if u.nickName === nickName
        v <- visitTableQuery if v.user === u.id
      } yield v).result
    db.run(query)
  }

  def getAllByPlaceId(id: Int): Future[Seq[Visit]] = {
    db.run(
      visitTableQuery
        .filter(visit => visit.place === id)
        .sortBy(_.date)
        .result)
  }

  def getAll: Future[Seq[Visit]] = {
    db.run(visitTableQuery.result)
  }
}