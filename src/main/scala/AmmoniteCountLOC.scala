import ammonite.ops._
import ImplicitWd._
import se_hpc.CountLOC

object AmmoniteCountLOC {
  def main(args: Array[String]): Unit = {
    val output = %%("cloc", "--xml", "--quiet", ".")
    val xmlDocument = output.out.lines drop (1) reduce (_ + "\n" + _)
    val cloc = CountLOC(xmlDocument)
    System.out.println("CountLOC Results")
    System.out.println(cloc)
  }

}
