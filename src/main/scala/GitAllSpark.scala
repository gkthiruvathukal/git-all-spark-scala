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
    val sc = new SparkContext()
    val repoURL = config.url.getOrElse("")
    val sourceFolder = config.src.getOrElse("")
    val destinationFolder = config.dst.getOrElse("")

    // Initial git clone is performed to srcRoot
    // This should be a shared folder on the Spark cluster

    val srcRoot = Path(new java.io.File(config.srcRoot.getOrElse("/projects/SE_HPC")))

    // The git fetch/git checkout (for each commit) is performed to dstRoot
    // This need not be shared among all nodes in the Spark cluster
    val dstRoot = Path(new java.io.File(config.dstRoot.getOrElse("/projects/SE_HPC")))

    // Because we analyze many projects, we require a subdirectory for each open source project to be analyzed.
    val sourcePath = srcRoot / sourceFolder

    if (exists ! srcRoot) {
      println("Source folder already exists (non-fatal)")
    } else {
      println("Creating non-existent root " + srcRoot.toString)
      mkdir ! srcRoot
    }

    // The use of Try here and elsewhere is to allow any phase to fail.
    // Ordinarily, failure would only occur if the srcRoot does not contain a valid clone.

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

      // The cache() here is to ensure this RDD is computed before moving onto the next phase.
      // RDD calculations are designed to be lazy.
      rddFetch.cache()
      rddFetch
    }

    val clocTime = simpleTimer {
      val rdd = hashFetchTime.result

      // List is used here to wrap the XML result so we can perform a reduce properly.
      // Temporary workaround for not being able to merge Seq[Node] easily (not performance-critical here)
      val rddCloc = rdd.map { gcp => List(doCloc(config, gcp).toXML) }
      rddCloc.cache()
      val result = rddCloc.reduce(_ ++ _)

      val clocReport = <cloc_report> { result.toSeq } </cloc_report>
      if (config.clocReportPath.isDefined)
        writeClocReport(config, clocReport)
      rdd.count()
    }

    val report = Report(
      localCloneTime.time / 1e9,
      hashFetchTime.time / 1e9,
      clocTime.time / 1e9,
      clocTime.result
    )

    val experiment = Experiment("git-all-spark-scala")
    if (config.xmlFilename.isDefined)
      writePerformanceReport(experiment, config, report)
  }

  /*
   * simpleTimer times a block of code and returns a generic TimedResult
   *
   * This demonstrates call-by-name style parameter passing in Scala.
   * The type of the block of code is inferred and returned as the result.
   */

  case class TimedResult[A](time: Double, result: A)

  def simpleTimer[A](block: => A): TimedResult[A] = {
    val t0 = System.nanoTime()

    // This runs the block of code
    val result = block

    val t1 = System.nanoTime()
    TimedResult(t1 - t0, result)
  }

  /*
   * Git Checkout Phase
   *
   * This is Phase 1 of the computation. For each commit, doGitCheckouts() is run by mapping the
   * initial RDD (of commits by position) to checkout each hash and stage it in the filesystem for
   * CLOC (count lines of code) analysis in Phase 2.
   *
   * The result of this phase is an RDD of GitCheckoutPhase results, which can also be reported to an XML file
   * for post processing.
   */
  case class GitCheckoutPhase(order: Int, commit: String, hostname: String, path: String, successful: Boolean, time: Double, usage: Int) {
    def toXML(): xml.Node = {
      <checkout>
        <order>{ order }</order>
        <commit>{ commit }</commit>
        <hostname>{ hostname }</hostname>
        <path>{ path }</path>
        <success>{ successful }</success>
        <time>{ time }</time>
        <usage>{ usage }</usage>
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
    val diskUsage = du(destinationPath)
    val currentPath = destinationPath / hash
    // TODO: Make disk usage threshold a command-line option.
    val checkout = config.checkout && diskUsage.percent < 90
    val hashCheckoutTime = simpleTimer {
      val success = if (checkout) {
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

    GitCheckoutPhase(id, hash, InetAddress.getLocalHost.getHostName, commitHashPath.toString, hashCheckoutTime.result, hashCheckoutTime.time / 1e9, diskUsage.percent)
  }

  def hashCodes(rootPath: Path, args: String): Array[String] = {
    val source = rootPath / args
    val log = %%("git", "log")(source)
    val logString = log.toString
    val logArray = logString.split("\n")
    val justHashCodes = logArray filter { line => line.startsWith("commit") } map { line => line.split(" ")(1) }

    return justHashCodes
  }

  /*
   * CLOC Phase - Count Lines of Code
   *
   * This is Phase 2. It assumes that the git checkouts have been staged. We'll know where the actual data
   * were staged by looking at the GitCheckoutPhase case class (struct) instance to inspect path. This
   * path will tell us the folder of the checkout.
   *
   * doCloc() runs the open source cloc tool to compute the various LOC metrics (lines, blank lines, comment lines)
   * on all supported languages. We then take this report and return a CountLOC case class instance, which contains
   * only the necessary information from the cloc tool's output.
   *
   * Our ultimate goal is to be able to support other analysis methods (including some of our own, under development).
   * So subsequent phases can add an analysis method like doCloc() and return a structure/report of information
   * similar to ClocPhase.
   */
  case class ClocPhase(order: Int, commit: String, cloc: Option[CountLOC], hostname: String, gcf: GitCheckoutPhase) {
    def toXML(): xml.Node = {
      <cloc_phase>
        <order>{ order }</order>
        <commit>{ commit }</commit>
        <hostname>{ hostname } </hostname>
        <path>{ gcf.toXML } </path>
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
    ClocPhase(gcp.order, gcp.commit, clocTime.result, InetAddress.getLocalHost().getHostName(), gcp)
  }

  def writeClocReport(config: Config, document: xml.Node) {
    val pprinter = new scala.xml.PrettyPrinter(80, 2) // scalastyle:ignore
    val file = new File(config.clocReportPath.get)
    val bw = new BufferedWriter(new FileWriter(file))
    println("Wrote cloc report file " + config.clocReportPath.get)
    bw.write(pprinter.format(document)) // scalastyle:ignore
    bw.close()
  }

  /*
   * This is for parsing the command line options. We have followed the pattern above by having the
   * command line option Config be usable to inspect the options set and for the purposes of reporting.
   */

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

  /*
   * Performance Report
   */

  case class Experiment(name: String) {
    def toXML(): xml.Elem = <experiment id={ name }/>
  }

  case class Report(cloneTime: Double, hashCheckoutTime: Double, clocTime: Double, commits: Long) {
    def toXML(): xml.Node = {
      <report>
        <time id="clone-time" t={ cloneTime.toString } unit="s"/>
        <time id="hash-checkout-time" t={ hashCheckoutTime.toString } avg={ (hashCheckoutTime / commits).toString } unit="s"/>
        <time id="cloc-time" t={ clocTime.toString } avg={ (clocTime / commits).toString } unit="s"/>
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

  /*
   * Utility for Examining Disk Usage
   */

  def du(path: Path): DiskUsage = {
    val usage = %%("df", "-Pkh", path)
    val lines = usage.out.lines
    val headings = lines(0).replace("Mounted on", "Mounted-on").split("\\s+")
    val fields = lines(1).split("\\s+")
    val dfMap = (headings zip fields) toMap

    val filesystem = dfMap.getOrElse("Filesystem", "")
    val size = dfMap.getOrElse("Size", "0G")
    val used = dfMap.getOrElse("Used", "0G")
    val avail = dfMap.getOrElse("Avail", "0G")
    val percent = dfMap.getOrElse("Use%", "0%")
    val mount = dfMap.getOrElse("Mounted-on", "")
    val percentMatcher = "\\d+%".r
    val storageMatcher = "\\d+(K|M|G|P)".r
    val sizeFound = storageMatcher.findFirstIn(size).get
    val usedFound = storageMatcher.findFirstIn(used).get
    val availFound = storageMatcher.findFirstIn(avail).get
    val percentFound = percentMatcher.findFirstIn(percent).get

    DiskUsage(
      filesystem,
      Storage(sizeFound.dropRight(1), sizeFound.last),
      Storage(usedFound.dropRight(1), usedFound.last),
      Storage(availFound.dropRight(1), availFound.last),
      percentFound.dropRight(1).toInt,
      mount
    )
  }

  case class Storage(amount: String, unit: Char)

  case class DiskUsage(fs: String, size: Storage, used: Storage, avail: Storage, percent: Int, mount: String)

}
