import ammonite.ops._
import ammonite.ops.ImplicitWd._

object folderAndDocUndo {
  def main(args: Array[String]): Unit = {
    val wd = pwd
    rm ! wd / 'example_folder
  }
}

