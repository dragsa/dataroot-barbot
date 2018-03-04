package databasemock

import acolyte.jdbc.Implicits._
import acolyte.jdbc.RowLists._
import acolyte.jdbc.{AcolyteDSL, Execution, QueryExecution, UpdateExecution, Driver => AcolyteDriver}
import org.gnat.barbot.models.{Bar, BarRepository}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import slick.jdbc.JdbcBackend.Database


class BarRepoSpec(implicit ee: ExecutionEnv) extends Specification {
  "Bar Repo persistence test suite" title

  val dummyTable = (rowList4(
    classOf[String] -> "name",
    classOf[String] -> "info_source",
    classOf[Boolean] -> "is_active",
    classOf[Int] -> "id")
    :+ ("c", "d", false, 1)
    :+ ("a", "b", false, 2)
    :+ ("e", "f", false, 3)
    )

  val handler = AcolyteDSL.handleStatement.withQueryDetection(
    "^select ") withQueryHandler { e: QueryExecution ⇒
    if (e.sql.startsWith("select ".toLowerCase)) {
      sqlLogger(e)
      dummyTable.asResult
    } else
      rowList1(classOf[String]).asResult
  } withUpdateHandler { e: UpdateExecution =>
    if (e.sql.startsWith("delete ")) {
      99
    }
    else if (e.sql.startsWith("update ")) {
      1
    } else {
      0
    }
  }

  private def sqlLogger(e: Execution) = {
    println("SQL: " + e.sql)
    println("Params: " + e.parameters)
  }

  AcolyteDriver.register("my-handler-id", handler)
  implicit val db = Database.forURL("jdbc:acolyte:anything-you-want?handler=my-handler-id")

  val barRepo = new BarRepository

  "select 1 bar by Id" in {
    barRepo.getOneById(3) must beSome(Bar("c", "d", false, Some(1))).await //(timeout = Duration.Inf)
  }

}
