import ammonite.ops._
import java.io._
import ammonite.ops.ImplicitWd._

object ammoniteImport {
  def main(args: Array[String]): Unit = {
    val workingDirectory = pwd.toString()
    println("You are in " + workingDirectory + " and it's contents are:")
    %ls
  }
}