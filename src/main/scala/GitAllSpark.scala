/*
 * GitAllSpark.scala - Analyze all Git commits in parallel on a cluster.
 */

package se_hpc

import org.apache.spark.SparkContext

import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.net._
import java.io._
import org.json4s.JsonDSL._
import scala.util._

object GitAllSparkScala {

  def main(args: Array[String]) {
    val config = parseCommandLine(args).getOrElse(Config())
    println("Configuration:")
    println(config.toXML)
    val experiment = Experiment("git-all-spark-scala")
    val sc = new SparkContext()
    val repoURL = config.url.getOrElse("")
    val sourceFolder = config.src.getOrElse("")
    val destinationFolder = config.dst.getOrElse("")

    // These defaults are for my cluster, which offers shared storage on /projects
    // You could also change these if you want to go shared nothing.

    val srcRoot = Path(new java.io.File(config.srcRoot.getOrElse("/projects/SE_HPC")))
    val dstRoot = Path(new java.io.File(config.dstRoot.getOrElse("/projects/SE_HPC")))

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

    val localCloneTime = simpleTimer {
      if (config.gitClone) {
        System.out.println("Cloning to " + srcRoot.toString)
        Try { %.git("clone", repoURL)(srcRoot) }
      }
    }

    val hashFetchTime = simpleTimer {
      val commits = hashCodes(srcRoot, sourceFolder)

      val rdd = sc.parallelize(config.start until commits.length by config.stride, config.nodes * config.cores)

      val rddFetch = rdd.map { pos => doGitCheckouts(config, pos, commits(pos)) }
      // .cache() needed to prevent re-evaluation.
      rddFetch.cache()
      rddFetch
    }

    val clocTime = simpleTimer {
      val rdd = hashFetchTime.result
      val rddCloc = rdd.map { gcp => List(doCloc(config, gcp).toXML) }
      rddCloc.cache()
      val result = rddCloc.reduce(_ ++ _)

      val clocReport = <cloc_report> { result.toSeq } </cloc_report>
      if (config.clocReportPath.isDefined)
        writeClocReport(config, clocReport)
      rdd.count() // force eval
    }

    printf("Clone time %.2f seconds", localCloneTime.time / 1.0e9)
    printf("Hash fetch time %.2f seconds", hashFetchTime.time / 1.0e9)

    val report = Report(
      localCloneTime.time / 1e9,
      hashFetchTime.time / 1e9,
      clocTime.result,
      (hashFetchTime.time / clocTime.result / 1e9),
      (clocTime.time / clocTime.result / 1e9)
    )
    if (config.xmlFilename.isDefined)
      writePerformanceReport(experiment, config, report)
  }

  /* Simple way of timing a block of code. Results are returned in a case class
   * where the time and result can be obtained through the corresponding fields.
   */
  case class TimedResult[A](time: Double, result: A)

  def simpleTimer[A](block: => A): TimedResult[A] = {
    val t0 = System.nanoTime()

    // This runs the block of code
    val result = block

    val t1 = System.nanoTime()
    TimedResult(t1 - t0, result)
  }

  case class GitCheckoutPhase(order: Int, commit: String, hostname: String, path: String, successful: Boolean, time: Double) {
    def toXML(): xml.Node = {
      <checkout>
        <order>{ order }</order>
        <commit>{ commit }</commit>
        <hostname>{ hostname }</hostname>
        <path>{ path }</path>
        <success>{ successful }</success>
        <time>{ time }</time>
      </checkout>
    }
  }

  def doGitCheckouts(config: Config, id: Int, hash: String): GitCheckoutPhase = {
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
      val success = if (config.checkout) {
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

        /* This above code is allowed to fail. If it does, we still want to know. */
        List(r1, r2, r3, r4) map { _.getOrElse(false) } reduce (_ && _)
      } else {
        true
      }
      if (success)
        System.out.println("doGitCheckouts(): git succeeded in cleckout of hash " + hash)
      else
        System.out.println("doGitCheckouts(): git failed in checkout of hash " + hash)
      success
    }

    val commitHashPath = destinationPath / hash

    GitCheckoutPhase(id, hash, InetAddress.getLocalHost.getHostName, commitHashPath.toString, hashCheckoutTime.result, hashCheckoutTime.time / 1e9)
  }

  /* The CLOC Phase is command-line option selectable. It therefore may or may not produce LOC info.
   * If it does, you'll be able to evaulate the option.
   * If it does not, you still get a ClocPhase result, but each of the results would be None.
   */

  case class ClocPhase(order: Int, commit: String, cloc: Option[CountLOC], hostname: String, path: String) {
    def toXML(): xml.Node = {
      <cloc_phase>
        <order>{ order }</order>
        <commit>{ commit }</commit>
        <hostname>{ hostname } </hostname>
        <path>{ path } </path>
        <report>{ Try { cloc.get.toXML } getOrElse (<cloc/>) }</report>
      </cloc_phase>
    }
  }

  def doCloc(config: Config, gcp: GitCheckoutPhase): ClocPhase = {
    val clocTime = simpleTimer {
      if (config.cloc) {
        val xmlResult = Try {
          val output = %%(config.clocPath.get, "--xml", "--quiet", gcp.path)
          output.out.lines drop (1) reduce (_ + "\n" + _)
        }
        Try { CountLOC(xmlResult.get) }.toOption
      } else {
        None
      }
    }
    ClocPhase(gcp.order, gcp.commit, clocTime.result, InetAddress.getLocalHost().getHostName(), gcp.path)
  }

  def parseCommandLine(args: Array[String]): Option[Config] = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("simplemap-spark-scala", "0.1.x")
      opt[String]("src") action { (x, c) =>
        c.copy(src = Some(x))
      } text ("src (String) is the name of the source folder (should match repo name) and not a path")
      opt[String]("dst") action { (x, c) =>
        c.copy(dst = Some(x))
      } text ("dst (String) is the name of the destination folder and not a path")
      opt[Int]("nodes") action { (x, c) =>
        c.copy(nodes = x)
      } text ("nodes (int) is the number of cluster nodes")
      opt[Int]("cores") action { (x, c) =>
        c.copy(cores = x)
      } text ("cores (int) is the number of cores on each cluster node")
      opt[String]("xml") action { (x, c) =>
        c.copy(xmlFilename = Some(x))
      } text (s"xml (string) is the name or path to a filename for writing the performance report")
      opt[String]("src-root") action { (x, c) =>
        c.copy(srcRoot = Some(x))
      } text ("src-root (String) is the base directory where the --src folder will be cloned")
      opt[String]("dst-root") action { (x, c) =>
        c.copy(dstRoot = Some(x))
      } text ("dst-root (String) is the base base directory where the --dst folder will be created for staging commits")
      opt[String]("url") action { (x, c) =>
        c.copy(url = Some(x))
      } text ("url (String) is the repo URL. This URL must work with git clone on your computer.")
      opt[Unit]("checkout") action { (x, c) =>
        c.copy(checkout = true)
      } text ("checkout (Boolean) indicates whether we should perform checkouts (default False)")
      opt[Unit]("cloc") action { (x, c) =>
        c.copy(cloc = true)
      } text ("cloc sets a flag to run the cloc line-counting tool")
      opt[String]("cloc-path") action { (x, c) =>
        c.copy(clocPath = Some(x))
      } text ("cloc-path (String) indicates the location of the cloc tool. Only used if cloc option is enabled.")
      opt[String]("cloc-report") action { (x, c) =>
        c.copy(clocReportPath = Some(x))
      } text ("cloc-report (String) is the path where to write the cloc report. Only used if cloc option is enabled.")
      opt[Unit]("git-clone") action { (x, c) =>
        c.copy(gitClone = true)
      } text ("git-clone indicates whether the clone is to be performed by the Spark driver code")
      opt[Int]("start") action { (x, c) =>
        c.copy(start = x)
      } text ("start (int) is the commit (by position) on master where to start (defaults to 0). Useful when you have extremely large repositories.")
      opt[Int]("stride") action { (x, c) =>
        c.copy(stride = x)
      } text ("stride (int) is how many commits to skip (by position) on master (defaults to 1). Useful when you have extremely large repositories.")
      opt[String]("github") action { (x, c) =>
        val gitPair = x.split("/")
        if (gitPair.length >= 2) {
          val org = gitPair(0)
          val repo = gitPair(1)
          c.copy(src = Some(repo), dst = Some(repo + "-commits"), url = Some(s"https://github.com/$org/$repo.git"), github = Some(x))
        } else
          c.copy(github = Some(x))
      } text ("github (String) is a user-org/repo-name; implies --url, --src, and --dst")
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

  case class Report(cloneTime: Double, hashCheckoutTime: Double, commits: Long, avgCheckoutTimePerCommit: Double, avgClocTimePerCommit: Double) {
    def toXML(): xml.Node = {
      <report>
        <time id="clone-time" t={ cloneTime.toString } unit="s"/>
        <time id="hash-checkout-time" t={ hashCheckoutTime.toString } unit="s"/>
        <time id="hash-checkout-time-per-commit" t={ avgCheckoutTimePerCommit.toString } unit="s"/>
        <time id="cloc-time-per-commit" t={ avgClocTimePerCommit.toString } unit="s"/>
        <commits n={ commits.toString }/>
      </report>
    }
  }

  def writePerformanceReport(exp: Experiment, config: Config, data: Report): Unit = {
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

  def writeClocReport(config: Config, document: xml.Node) {
    val pprinter = new scala.xml.PrettyPrinter(80, 2) // scalastyle:ignore
    val file = new File(config.clocReportPath.get)
    val bw = new BufferedWriter(new FileWriter(file))
    println("Wrote cloc report file " + config.clocReportPath.get)
    bw.write(pprinter.format(document)) // scalastyle:ignore
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
      checkout: Boolean = false,
      cloc: Boolean = false,
      clocPath: Option[String] = Some("/usr/bin/cloc"),
      clocReportPath: Option[String] = None,
      start: Int = 0,
      stride: Int = 1,
      gitClone: Boolean = false,
      xmlFilename: Option[String] = None,
      github: Option[String] = None
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
        <property key="github" value={ github.getOrElse("") }/>
        <property key="checkout" value={ checkout.toString }/>
        <property key="cloc" value={ cloc.toString }/>
        <property key="clocPath" value={ clocPath.getOrElse("").toString }/>
        <property key="start" value={ start.toString }/>
        <property key="stride" value={ stride.toString }/>
        <property key="git-clone" value={ gitClone.toString }/>
        <property key="xml" value={ xmlFilename.getOrElse("") }/>
      </config>
    }

  }
}
