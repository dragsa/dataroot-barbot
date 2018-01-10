package org.gnat.barbot.models

import java.sql.Timestamp

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.Future

//case class User(nickName: Option[String],
//                firstName: String,
//                lastName: Option[String],
//                gender: Option[String] = None,
//                phone: Option[String] = None,
//                email: Option[String] = None,
//                favoriteDrink: Option[String] = None,
//                favoriteMeal: Option[String] = None,
//                id: Int)

class UserTable(tag: Tag) extends Table[User](tag, "users") {

  // TODO well, unique and null... should be OK according to Postgres view
  def nickName = column[Option[String]]("nick_name", O.Unique, O.Length(100))

  def firstName = column[String]("first_name", O.Length(50))

  def lastName = column[Option[String]]("last_name", O.Length(50))

  def gender = column[Option[String]]("gender", O.Length(1))

  def phone = column[Option[String]]("phone", O.Unique, O.Length(50))

  def email = column[Option[String]]("email", O.Unique, O.Length(100))

  def favoriteDrink = column[Option[String]]("fav_drink", O.Length(50))

  def favoriteMeal = column[Option[String]]("fav_meal", O.Length(50))

  def id = column[Int]("id", O.PrimaryKey, O.Unique)

  def * =
    (nickName, firstName, lastName, gender, phone, email, favoriteDrink, favoriteMeal, id) <> (User.apply _ tupled, User.unapply)

}

object UserTable {
  val table = TableQuery[UserTable]
}

class UserRepository(implicit db: Database) {
  val userTableQuery = UserTable.table

  def createOne(user: User): Future[User] = {
    db.run(userTableQuery returning userTableQuery += user)
  }

  def createMany(users: List[User]): Future[Seq[User]] = {
    db.run(userTableQuery returning userTableQuery ++= users)
  }

  def updateOne(user: User): Future[Int] = {
    db.run(
      userTableQuery
        .filter(_.id === user.id)
        .update(user))
  }

  def getOneById(id: Int): Future[Option[User]] = {
    db.run(userTableQuery.filter(_.id === id).result.headOption)
  }

  def getOneByNickname(nickName: String): Future[Option[User]] = {
    db.run(userTableQuery.filter(_.nickName === nickName).result.headOption)
  }

  def getAll: Future[Seq[User]] = {
    db.run(userTableQuery.result)
  }
}
