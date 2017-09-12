import ammonite.ops._
import ammonite.ops.ImplicitWd._
import scala.collection.mutable.ListBuffer
import scala.io.StdIn.readLine
import scopt.OptionParser

object allCommits {
  def main(args: Array[String]): Unit = {
    //Gets information about repo so that all commits can be cloned
    val config = parseCommandLine(args).getOrElse(Config())

    val repoURL = config.url.getOrElse("")
    val sourceFolder = config.src.getOrElse("")
    val destinationFolder = config.dst.getOrElse("")
    val startHash = config.start.getOrElse(0)
    val stride = config.stride.getOrElse(1)

    // These defaults are for use on our cluster...
    val srcRoot = Path(new java.io.File(config.srcRoot.getOrElse("/projects/SE_HPC")))
    val dstRoot = Path(new java.io.File(config.dstRoot.getOrElse("/scratch/SE_HPC")))

    //Establishes path for source folder where clone occurs and destination folder which will recieve every commit
    val sourcePath = srcRoot / sourceFolder
    val destinationPath = dstRoot / destinationFolder

    //Checks whether these two folders already exist and if so exits the program and alerts the user
    if (exists ! sourcePath) {
      println("Source folder already exists cannot execute program")
      System.exit(1)
    }
    if (exists ! destinationPath) {
      println("Destination folder already exists cannot execute program")
      System.exit(1)
    }

    //Clones repo into source folder
    %.git("clone", repoURL)(srcRoot)

    //Gets the hashs for each commit and prepares them as an iterator
    val logForList = hashCodes(srcRoot, sourceFolder).toList
    val currentNodeHashes = for (i <- (startHash to logForList.size - 1 by stride).toList) yield logForList(i)
    val logIterator = currentNodeHashes.toIterator

    //Creates folder where all commits will be placed as subfolders
    mkdir ! dstRoot / destinationFolder

    //This loop creates a new folder for each commit and fills it with the files that were current as of that commit
    for (currentHash <- logIterator) {
      mkdir ! destinationPath / currentHash
      var currentPath = destinationPath / currentHash
      %.git('init)(currentPath)
      %%("git", "remote", "add", "upstream", sourcePath)(currentPath)
      %%("git", "fetch", "upstream")(currentPath)
      %%("git", "checkout", currentHash)(currentPath)
      if (config.cloc) {
        var lines = %%("cloc", currentPath)(destinationPath)
        println(lines)
      }
    }
  }

  //This method takes the name of the folder where the repo is cloned and returns the hash codes
  def hashCodes(rootPath: Path, args: String): Array[String] = {
    val source = rootPath / args
    val log = %%("git", "log")(source)
    val logString = log.toString
    val logArray = logString.split("\n")
    val justHashCodes = logArray filter { line => line.startsWith("commit") } map { line => line.split(" ")(1) }

    return justHashCodes
  }

  def parseCommandLine(args: Array[String]): Option[Config] = {
    val parser = new scopt.OptionParser[Config]("scopt") {
      head("allCommits", "1.0")
      opt[String]('s', "src") action { (x, c) =>
        c.copy(src = Some(x))
      } text ("s/src is a String property")
      opt[String]('d', "dst") action { (x, c) =>
        c.copy(dst = Some(x))
      } text ("d/dst is a String property")
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
      opt[Int]("stride") action { (x, c) => c.copy(stride = Some(x))
      } text ("stride is an Int property")
      opt[Int]("start") action { (x, c) => c.copy(start = Some(x))
      } text ("start is an Int property")
    }
    parser.parse(args, Config())
  }

  case class Config(
    src: Option[String] = None,
    dst: Option[String] = None,
    srcRoot: Option[String] = None,
    dstRoot: Option[String] = None,
    url: Option[String] = None,
    start: Option[Int] = None,
    stride: Option[Int] = None,
    cloc: Boolean = false
  )

}

