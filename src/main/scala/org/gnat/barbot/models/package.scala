package org.gnat.barbot

import java.sql.Timestamp


package object models {

  case class User(nickName: Option[String],
                  firstName: String,
                  lastName: Option[String],
                  gender: Option[String] = None,
                  phone: Option[String] = None,
                  email: Option[String] = None,
                  favoriteDrink: Option[String] = None,
                  favoriteMeal: Option[String] = None,
                  id: Int)

  case class Bar(name: String,
                 infoSource: String,
                 isActive: Boolean = true,
                 id: Option[Int] = None)

  case class Visit(user: Int,
                   place: Int,
                   mark: Int,
                   date: Timestamp,
                   id: Int)

  case class Flow(name: String,
                  steps: String,
                  description: String,
                  id: Option[Int] = None)

}
