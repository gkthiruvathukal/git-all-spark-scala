import ammonite.ops._
import ImplicitWd._
import se_hpc.CountLOC
import pprint._
import java.io._

object AmmoniteCountLOC {
  def main(args: Array[String]): Unit = {
    val output = %%("cloc", "--xml", "--quiet", "src")
    val xmlDocument = output.out.lines drop (1) reduce (_ + "\n" + _)
    val cloc = CountLOC(xmlDocument)
    System.out.println("CountLOC Results")
    val results = cloc.toXML()
    val pprinter = new scala.xml.PrettyPrinter(80, 2) // scalastyle:ignore
    val sw = new StringWriter()
    sw.write(pprinter.format(results))
    System.out.println(sw)
  }

}
