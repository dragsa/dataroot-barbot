package org.gnat.barbot.models

import java.sql.Timestamp

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.Future

//  case class User(firstName: String,
//                  lastName: String,
//                  birthDate: Timestamp,
//                  gender: String,
//                  phone: String,
//                  email: String,
//                  favoriteDrink: Option[String],
//                  favoriteMeal: Option[String],
//                  id: Int)

class UserTable(tag: Tag) extends Table[User](tag, "users") {

  def nickName = column[String]("nick_name", O.Unique, O.Length(100))

  def firstName = column[String]("first_name", O.Length(50))

  def lastName = column[String]("last_name", O.Length(50))

  def birthDate = column[Timestamp]("birth_date")

  def gender = column[String]("gender", O.Length(1))

  def phone = column[String]("phone", O.Unique, O.Length(50))

  def email = column[String]("email", O.Unique, O.Length(100))

  def favoriteDrink = column[String]("fav_drink", O.Length(50))

  def favoriteMeal = column[String]("fav_meal", O.Length(50))

  def id = column[Int]("id", O.PrimaryKey, O.Unique, O.AutoInc)

  def * =
    (nickName, firstName.?, lastName.?, birthDate, gender, phone, email, favoriteDrink.?, favoriteMeal.?, id) <> (User.apply _ tupled, User.unapply)

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
