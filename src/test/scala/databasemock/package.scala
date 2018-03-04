import acolyte.jdbc.{Execution, RowList4}
import org.gnat.barbot.models.Bar

package object databasemock {

  def appender(schema: RowList4.Impl[String, String, Boolean, Int], listToAppend: List[Bar]): RowList4.Impl[String, String, Boolean, Int] = {
    listToAppend match {
      case Nil => schema
      case head :: tail => appender(schema.append(head.name, head.infoSource, head.isActive, head.id.get), tail)
    }
  }

  def sqlLogger(e: Execution) = {
    println("SQL: " + e.sql)
    println("Params: " + e.parameters)
  }
}
