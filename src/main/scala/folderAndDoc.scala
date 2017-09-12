import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.io._

object folderAndDoc {
  def main(args: Array[String]): Unit = {
    val workingDirectory = pwd
    mkdir ! workingDirectory / 'example_folder
    write(workingDirectory / 'example_folder / "file1.txt", "Ammonites are among the most prolific artifacts today")
  }
}