import ammonite.ops._
import ImplicitWd._
import se_hpc.CountLOC
import pprint._

object AmmoniteCountLOC {
  def main(args: Array[String]): Unit = {
    val output = %%("cloc", "--xml", "--quiet", "src")
    val xmlDocument = output.out.lines drop (1) reduce (_ + "\n" + _)
    val cloc = CountLOC(xmlDocument)
    System.out.println("CountLOC Results")
    val text = pprint.stringify(cloc)
    System.out.println(text)
  }

}
