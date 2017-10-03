
package se_hpc

case class CountLOC(header: Header, languages: Seq[Language])

case class Header(clocUrl: String, clocVersion: String, elapsedSeconds: Double, nFiles: Long, nLines: Long,
  filesPerSecond: Double, linesPerSecond: Double)

case class Language(name: String, filesCount: Long, blank: Long, comment: Long, code: Long)

object CountLOC {
  def apply(text: String): CountLOC = {

    val xmlDocument = scala.xml.XML.loadString(text)

    /* We don't need most of these, but I don't want to have to do the work later in case we do. */

    val header = (xmlDocument \ "header")(0)
    val cloc_url = (header \ "cloc_url").text
    val cloc_version = (header \ "cloc_version").text
    val elapsed_seconds = (header \ "elapsed_seconds").text.toDouble
    val n_files = (header \ "n_files").text.toLong
    val n_lines = (header \ "n_lines").text.toLong
    val files_per_second = (header \ "files_per_second").text.toDouble
    val lines_per_second = (header \ "lines_per_second").text.toDouble

    val languages = (xmlDocument \ "languages" \ "language").map { node =>
      Language(
        node.attribute("name").get(0).text,
        node.attribute("files_count").get(0).text.toLong,
        node.attribute("blank").get(0).text.toLong,
        node.attribute("comment").get(0).text.toLong,
        node.attribute("code").get(0).text.toLong
      )
    }

    CountLOC(Header(cloc_url, cloc_version, elapsed_seconds, n_files, n_lines, files_per_second, lines_per_second), languages)
  }

  // TODO: Make unit tests for this...
  def main(args: Array[String]): Unit = {
    val testDocument =
      """<?xml version="1.0"?><results>
        |<header>
        |  <cloc_url>github.com/AlDanial/cloc</cloc_url>
        |  <cloc_version>1.74</cloc_version>
        |  <elapsed_seconds>1.29397416114807</elapsed_seconds>
        |  <n_files>209</n_files>
        |  <n_lines>20971</n_lines>
        |  <files_per_second>161.517908375053</files_per_second>
        |  <lines_per_second>16206.6605575753</lines_per_second>
        |</header>
        |<languages>
        |  <language name="XML" files_count="197" blank="0" comment="0" code="19618" />
        |  <language name="XSLT" files_count="1" blank="30" comment="16" code="468" />
        |  <language name="Scala" files_count="5" blank="57" comment="20" code="326" />
        |  <language name="CSS" files_count="1" blank="51" comment="21" code="207" />
        |  <language name="Bourne Shell" files_count="3" blank="22" comment="43" code="63" />
        |  <language name="make" files_count="1" blank="7" comment="2" code="18" />
        |  <language name="Markdown" files_count="1" blank="0" comment="0" code="2" />
        |  <total sum_files="209" blank="167" comment="102" code="20702" />
        |</languages>
        |</results>
      """.stripMargin

    System.out.println(CountLOC(testDocument))
  }

}