import ammonite.ops._
import ammonite.ops.ImplicitWd._

object Report {

  def main(args: Array[String]): Unit = {
    val experimentName = "astropy-cloc"
    val files = ls ! pwd / "experiments" toVector
    val expFiles = files filter { _.name.startsWith(experimentName) }
    val docs = expFiles map { _.toIO } map { scala.xml.XML.loadFile(_) }
    // to be continued
  }

}
