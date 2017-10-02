/*
 * GitAllSpark.scala - Analyze all Git commits in parallel on a cluster.
 */

package se_hpc

import org.apache.spark.SparkContext

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import org.json4s.JsonDSL._
import java.net.InetAddress

object GitAllSparkScala {

  def simpleTimer[A](block: => A): (A, Double) = {
    val t0 = System.nanoTime()

    // This runs the block of code
    val result = block

    val t1 = System.nanoTime()
    (result, t1 - t0)
  }

  def main(args: Array[String]) {
    val config = parseCommandLine(args).getOrElse(Config())
    val experiment = Experiment("git-all-spark-scala")
    val sc = new SparkContext()
    val repoURL = config.url.getOrElse("")
    val sourceFolder = config.src.getOrElse("")
    val destinationFolder = config.dst.getOrElse("")

    // These defaults are for use on the Cooley cluster...
    val srcRoot = Path(new java.io.File(config.srcRoot.getOrElse("/projects/SE_HPC")))
    //val dstRoot = Path(new java.io.File(config.dstRoot.getOrElse("/scratch/SE_HPC")))

    //Establishes path for source folder where clone occurs and destination folder which will recieve every commit
    val sourcePath = srcRoot / sourceFolder

    // Initial clone takes place on the driver.
    // The hash fetches take place on the nodes.
    if (exists ! srcRoot) {
      println("Source folder already exists - might be ok")
    } else {
      println("Creating non-existent root " + srcRoot.toString)
      mkdir ! srcRoot
    }

    //Clones repo into source folder

    val localcloneTime = simpleTimer {
      if (config.gitClone) {
        System.out.println("Cloning to " + srcRoot.toString)
        %.git("clone", repoURL)(srcRoot)
      }
    }

    val hashFetchTime = simpleTimer {
      val rdd = sc.parallelize(hashCodes(srcRoot, sourceFolder), config.nodes * config.cores)

      val rdd2 = rdd.map { hash => doGitClone(config, hash).toString }

      val result = rdd2.reduce(_ + "\n" + _)

      println(result)
    }

    printf("Clone time %.2f seconds", localcloneTime._2 / 1.0e9)
    printf("Hash fetch time %.2f seconds", hashFetchTime._2 / 1.0e9)

  }

  case class Info(hostname: String, path: String, hashCheckoutTime: Double, clocTime: Double)

  def doGitClone(config: Config, hash: String): Info = {
    val srcRoot = Path(new java.io.File(config.srcRoot.getOrElse("/projects/SE_HPC")))
    val sourceFolder = config.src.getOrElse("")
    val sourcePath = srcRoot / sourceFolder
    val destRoot = Path(new java.io.File(config.dstRoot.getOrElse("/scratch/SE_HPC")))
    val destinationFolder = config.dst.getOrElse(InetAddress.getLocalHost().getHostName())
    val destinationPath = destRoot / destinationFolder

    if (!(exists ! destinationPath)) {
      mkdir ! destinationPath
    }
    mkdir ! destinationPath / hash
    var currentPath = destinationPath / hash
    val hashCheckoutTime = simpleTimer {
      %.git('init)(currentPath)
      %%("git", "remote", "add", "upstream", sourcePath)(currentPath)
      %%("git", "fetch", "upstream")(currentPath)
      %%("git", "checkout", hash)(currentPath)
    }

    val clocTime = simpleTimer {
      if (config.cloc) {
        val lines = %%("cloc", currentPath)(destinationPath)
        println(lines)
      }
    }
    val commitHashPath = destinationPath / hash
    Info(InetAddress.getLocalHost.getHostName, commitHashPath.toString, hashCheckoutTime._2, clocTime._2)
  }

  def parseCommandLine(args: Array[String]): Option[Config] = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("simplemap-spark-scala", "0.1.x")
      opt[String]('s', "src") action { (x, c) =>
        c.copy(src = Some(x))
      } text ("s/src is a String property")
      opt[String]('d', "dst") action { (x, c) =>
        c.copy(dst = Some(x))
      } text ("d/dst is a String property")
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
      opt[String]("src-root") action { (x, c) =>
        c.copy(srcRoot = Some(x))
      } text ("srcRoot is a base directory for cloning stuff")
      opt[String]("dst-root") action { (x, c) =>
        c.copy(dstRoot = Some(x))
      } text ("dstRoot is a base directory for fetching hashes")
      opt[String]('u', "url") action { (x, c) =>
        c.copy(url = Some(x))
      } text ("u/url is a String property")
      opt[Unit]("cloc") action { (x, c) =>
        c.copy(cloc = true)
      }
      opt[Unit]("git-clone") action { (x, c) =>
        c.copy(gitClone = true)
      }
      help("help") text ("prints this usage text")
    }
    parser.parse(args, Config())
  }

  def hashCodes(rootPath: Path, args: String): Array[String] = {
    val source = rootPath / args
    val log = %%("git", "log")(source)
    val logString = log.toString
    val logArray = logString.split("\n")
    val justHashCodes = logArray filter { line => line.startsWith("commit") } map { line => line.split(" ")(1) }

    return justHashCodes
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
      cores: Int = 12,
      generate: Boolean = false,
      blocks: Int = 1,
      blockSize: Int = 1, // 1 MB
      nparts: Int = 1,
      size: Int = 1,
      nodes: Int = 1,
      jsonFilename: Option[String] = None,
      xmlFilename: Option[String] = None,
      srcRoot: Option[String] = None,
      dstRoot: Option[String] = None,
      url: Option[String] = None,
      start: Option[Int] = None,
      stride: Option[Int] = None,
      cloc: Boolean = false,
      gitClone: Boolean = false
  ) {

    def toXML(): xml.Elem = {
      <config>
        <property key="src" value={ src.getOrElse("") }/>
        <property key="dst" value={ src.getOrElse("") }/>
        <property key="cores" value={ cores.toString }/>
        <property key="generate" value={ generate.toString }/>
        <property key="blocks" value={ blocks.toString }/>
        <property key="blockSize" value={ blockSize.toString } unit="MB"/>
        <property key="nparts" value={ nparts.toString }/>
        <property key="size" value={ size.toString }/>
        <property key="nodes" value={ nodes.toString }/>
        <property key="json" value={ jsonFilename.getOrElse("") }/>
        <property key="xml" value={ xmlFilename.getOrElse("") }/>
      </config>
    }

    def toJSON(): org.json4s.JsonAST.JObject = {
      val properties = ("src" -> src.getOrElse("")) ~ ("dst" -> dst.getOrElse("")) ~ ("cores" -> cores.toString) ~
        ("generate" -> generate.toString) ~ ("blocks" -> blocks.toString) ~ ("blockSize" -> blockSize.toString) ~
        ("blockSizeUnit" -> "MB") ~
        ("nparts" -> nparts.toString) ~ ("size" -> size.toString) ~ ("nodes" -> nodes.toString) ~
        ("jsonFilename" -> jsonFilename.getOrElse("")) ~ ("xmlFilename" -> xmlFilename.getOrElse(""))
      ("config" -> properties)
    }

  }
}
