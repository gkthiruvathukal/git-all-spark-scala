/*
 * GitAllSpark.scala - Analyze all Git commits in parallel on a cluster.
 */

package se_hpc

//import blockperf._
import org.apache.spark.SparkContext
import org.apache.spark.input.PortableDataStream
import org.apache.spark.rdd.RDD
import scala.util.{ Try, Success, Failure }
import java.io._
//import breeze.linalg._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import java.net.InetAddress

object GitAllSparkScala {

  def main(args: Array[String]) {
    val config = parseCommandLine(args).getOrElse(Config())
    val experiment = Experiment("simplemap-spark-scala")
    val sc = new SparkContext()

    val rdd = sc.parallelize(1 to config.files, config.nodes * config.cores)

    val rdd2 = rdd.map { i => makeScratchDir(i).loc }

    val sumLOC = rdd2.reduce(_ + _)
    println(s"cumulative LOC = $sumLOC")
  }

  case class Info(hostname: String, path: String, loc: Int)

  def makeScratchDir(id: Int): Info = {
    import ammonite.ops._
    import ammonite.ops.ImplicitWd._

    val path = root / "scratch" / "SE_HPC"
    mkdir ! path
    val path2 = root / "scratch" / "SE_HPC" / id.toString
    mkdir ! path2
    val filePath = path2 / "AmmoniteExample.scala"
    println(s"Writing to $filePath")
    write(filePath, """|object amm extends App {
                       |   ammonite.Main().run()
                       |}
                       |""".stripMargin('|'))

    println(s"wc -l $filePath")
    val result = %%('wc, "-l", filePath)

    println(result)
    val crudeLOC = result.out.string.split(" ")(0).toInt
    Info(InetAddress.getLocalHost().getHostName(), path2.toString, crudeLOC)
  }

  def parseCommandLine(args: Array[String]): Option[Config] = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("simplemap-spark-scala", "0.1.x")
      opt[String]('s', "src") action { (x, c) =>
        c.copy(src = Some(x))
      } text ("s/src is a String property")
      opt[Unit]('g', "generate") action { (_, c) =>
        c.copy(generate = true)
      } text ("g/generate is a Boolean property")
      opt[String]('d', "dst") action { (x, c) =>
        c.copy(dst = Some(x))
      } text ("d/dst is a String property")
      opt[Int]('f', "files") action { (x, c) =>
        c.copy(files = x)
      } text ("f/files is an int property")
      opt[Int]('n', "nodes") action { (x, c) =>
        c.copy(nodes = x)
      } text ("n/nodes is an int property")
      opt[Int]('c', "cores") action { (x, c) =>
        c.copy(cores = x)
      } text ("c/cores is an int property (default to 12 for dual-hexcore on Cooley)")
      opt[String]('j', "json") action { (x, c) =>
        c.copy(jsonFilename = Some(x))
      } text (s"json <filename>is where to write JSON reports")
      opt[String]('x', "xml") action { (x, c) =>
        c.copy(xmlFilename = Some(x))
      } text (s"xml <filename> is where to write XML reports")
      help("help") text ("prints this usage text")
    }
    parser.parse(args, Config())
  }

  case class Experiment(name: String) {
    def toXML(): xml.Elem = <experiment id={ name }/>
    def toJSON(): org.json4s.JsonAST.JObject = ("experiment" -> ("id" -> name))
  }

  case class Report(mapTime: Double, shiftTime: Double, avgTime: Double) {
    def toXML(): xml.Node = {
      <report>
        <time id="mapTime" t={ mapTime.toString } unit="ns"/>
        <time id="shiftTime" t={ shiftTime.toString } unit="ns"/>
        <time id="avgTime" t={ avgTime.toString } unit="ns"/>
      </report>
    }

    def toJSON(): org.json4s.JsonAST.JObject = {
      val timeData = ("mapTime" -> mapTime.toString) ~ ("shiftTime" -> shiftTime.toString) ~ ("avgTime" -> avgTime.toString)
      ("report" -> timeData)
    }
  }

  // command-line parameters

  case class Config(
      src: Option[String] = None,
      dst: Option[String] = None,
      generate: Boolean = false,
      blocks: Int = 1,
      blockSize: Int = 1, // 1 MB
      nodes: Int = 1,
      cores: Int = 12,
      files: Int = 1,
      jsonFilename: Option[String] = None,
      xmlFilename: Option[String] = None
  ) {

    def toXML(): xml.Elem = {
      <config>
        <property key="src" value={ src.getOrElse("") }/>
        <property key="dst" value={ src.getOrElse("") }/>
        <property key="cores" value={ cores.toString }/>
        <property key="generate" value={ generate.toString }/>
        <property key="blocks" value={ blocks.toString }/>
        <property key="blockSize" value={ blockSize.toString } unit="MB"/>
        <property key="files" value={ files.toString }/>
        <property key="nodes" value={ nodes.toString }/>
        <property key="json" value={ jsonFilename.getOrElse("") }/>
        <property key="xml" value={ xmlFilename.getOrElse("") }/>
      </config>
    }

    def toJSON(): org.json4s.JsonAST.JObject = {
      val properties = ("src" -> src.getOrElse("")) ~ ("dst" -> dst.getOrElse("")) ~ ("cores" -> cores.toString) ~
        ("generate" -> generate.toString) ~ ("blocks" -> blocks.toString) ~ ("blockSize" -> blockSize.toString) ~
        ("blockSizeUnit" -> "MB") ~
        ("files" -> files.toString) ~ ("nodes" -> nodes.toString) ~
        ("jsonFilename" -> jsonFilename.getOrElse("")) ~ ("xmlFilename" -> xmlFilename.getOrElse(""))
      ("config" -> properties)
    }

  }
}
