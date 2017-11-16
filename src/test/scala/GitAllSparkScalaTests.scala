package se_hpc

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import org.scalatest._

class SparkBechmarkHPCTests extends FlatSpec with Matchers {

  "true" should "be true" in {
    true should be(true)
  }

  "df explorations" should "be placed in a dictionary (Map)" in {
    val dfOutput = """Filesystem                  Size  Used Avail Use% Mounted on
        |/dev/mapper/system-scratch  345G  126G  202G  39% /scratch
        |""".stripMargin.replace("Mounted on", "Mounted-on")

    val lines = dfOutput.split("\n")
    val headings = lines(0).split("\\s+")
    val fields = lines(1).split("\\s+")
    val dfMap = (headings zip fields) toMap

    headings should be(Array("Filesystem", "Size", "Used", "Avail", "Use%", "Mounted-on"))
    fields.length should be(headings.length)
    dfMap.get("Filesystem") should be(Some("/dev/mapper/system-scratch"))
    dfMap.get("Size") should be(Some("345G"))
    dfMap.get("Used") should be(Some("126G"))
    dfMap.get("Avail") should be(Some("202G"))
    dfMap.get("Use%") should be(Some("39%"))
    dfMap.get("Mounted-on") should be(Some("/scratch"))
  }
}
