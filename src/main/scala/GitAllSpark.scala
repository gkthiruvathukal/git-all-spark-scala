/*
 * GitAllSpark.scala - Analyze all Git commits in parallel on a cluster.
 */

package se_hpc

import org.apache.spark.SparkContext

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import org.json4s.JsonDSL._
import java.net.InetAddress
import java.io.File
import java.io._
import scala.util.Try

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
      val commits = hashCodes(srcRoot, sourceFolder)

      val rdd = sc.parallelize(config.start until commits.length by config.stride, config.nodes * config.cores)

      val rdd2 = rdd.map { pos => doGitClone(config, commits(pos)).toString }

      val result = rdd2.reduce(_ + "\n" + _)

      println(result)
      rdd.count()
    }

    printf("Clone time %.2f seconds", localcloneTime._2 / 1.0e9)
    printf("Hash fetch time %.2f seconds", hashFetchTime._2 / 1.0e9)

    val report = Report(localcloneTime._2 / 1e9, hashFetchTime._2 / 1e9, hashFetchTime._1, (hashFetchTime._2 / hashFetchTime._1 / 1e9))
    if (config.xmlFilename.isDefined)
      writeXmlReport(experiment, config, report)

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
      val r1 = Try {
        System.out.println("git init " + currentPath.toString)
        %.git('init)(currentPath)
        true
      }

      val r2 = Try {
        System.out.println("git remote add upstream " + sourcePath.toString + " " + currentPath.toString)
        %%("git", "remote", "add", "upstream", sourcePath)(currentPath)
        true
      }
      val r3 = Try {
        System.out.println("git fetch upstream " + currentPath.toString)
        %%("git", "fetch", "upstream")(currentPath)
        true
      }

      val r4 = Try {
        System.out.println("git checkout " + currentPath.toString + " " + hash)
        %%("git", "checkout", hash)(currentPath)
        true
      }

      val success = r1.getOrElse(false) && r2.getOrElse(false) && r3.getOrElse(false) && r4.getOrElse(false)
      if (success)
        System.out.println("doGitClone(): git succeeded in cleckout of hash " + hash)
      else
        System.out.println("doGitClone(): git failed in checkout of hash " + hash)
    }

    val clocTime = simpleTimer {
      if (config.cloc) {
        val lines = %%("cloc", currentPath)(destinationPath)
        println(lines)
      }
    }
    val commitHashPath = destinationPath / hash
    Info(InetAddress.getLocalHost.getHostName, commitHashPath.toString, hashCheckoutTime._2 / 1e9, clocTime._2 / 1e9)
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
      opt[Int]("start") action { (x, c) =>
        c.copy(start = x)
      } text ("start is an int property")
      opt[Int]("stride") action { (x, c) =>
        c.copy(stride = x)
      } text ("stride is an int property")
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

  case class Report(cloneTime: Double, hashCheckoutTime: Double, commits: Long, avgTimePerCommit: Double) {
    def toXML(): xml.Node = {
      <report>
        <time id="clone-time" t={ cloneTime.toString } unit="s"/>
        <time id="hash-fetch-time" t={ hashCheckoutTime.toString } unit="s"/>
        <time id="hash-fetch-time-per-commit" t={ avgTimePerCommit.toString } unit="s"/>
        <commits n={ commits.toString }/>
      </report>
    }
  }

  def writeXmlReport(exp: Experiment, config: Config, data: Report): Unit = {
    val results = <results>
                    { exp.toXML }{ config.toXML }{ data.toXML }
                  </results>
    val pprinter = new scala.xml.PrettyPrinter(80, 2) // scalastyle:ignore
    val file = new File(config.xmlFilename.get)
    val bw = new BufferedWriter(new FileWriter(file))
    println("Wrote to XML file " + config.xmlFilename.get)
    bw.write(pprinter.format(results)) // scalastyle:ignore
    bw.close()
  }

  // command-line parameters

  case class Config(
      src: Option[String] = None,
      dst: Option[String] = None,
      cores: Int = 4,
      nodes: Int = 1,
      srcRoot: Option[String] = None,
      dstRoot: Option[String] = None,
      url: Option[String] = None,
      cloc: Boolean = false,
      start: Int = 0,
      stride: Int = 1,
      gitClone: Boolean = false,
      xmlFilename: Option[String] = None
  ) {

    def toXML(): xml.Elem = {
      <config>
        <property key="src" value={ src.getOrElse("") }/>
        <property key="dst" value={ dst.getOrElse("") }/>
        <property key="cores" value={ cores.toString }/>
        <property key="nodes" value={ nodes.toString }/>
        <property key="src-root" value={ srcRoot.getOrElse("") }/>
        <property key="dst-root" value={ dstRoot.getOrElse("") }/>
        <property key="url" value={ url.getOrElse("") }/>
        <property key="cloc" value={ cloc.toString }/>
        <property key="start" value={ start.toString }/>
        <property key="stride" value={ stride.toString }/>
        <property key="git-clone" value={ gitClone.toString }/>
        <property key="xml" value={ xmlFilename.getOrElse("") }/>
      </config>
    }

  }
}
