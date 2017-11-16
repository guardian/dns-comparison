import java.io.{ByteArrayInputStream, File, InputStream}

import org.xbill.DNS.{Master, Type, Record => JRecord}

import scala.io.Source

/** Adaptor to turn a BIND master file parser into a Scala iterator of DNS
  * records
  */
class MasterIterator(master: Master) extends Iterator[JRecord] {
  var nextRecord = Option(master.nextRecord())
  def hasNext = nextRecord.nonEmpty
  def next() = {
    val next = nextRecord.get
    nextRecord = Option(master.nextRecord())
    next
  }
}

/* Case class representing a DNS record */
case class Record(name: String, ttl: Long, typeName: String, resourceRecord: String)
object Record {
  /* apply method that takes a dnsjava record type */
  def apply(jr: JRecord): Record = {
    val rType = Type.string(jr.getType)
    val rr = if (rType == "TXT") jr.rdataToString.stripPrefix("\"").stripSuffix("\"") else jr.rdataToString
    Record(jr.getName.toString.stripSuffix(".").toLowerCase, jr.getTTL, rType, rr.stripSuffix("."))
  }
}

object Dyn {
  /* This is an almighty hack to deal with Dyn giving us CNAME records that have priorities */
  def cleanDynCname(line: String): String = {
    val DynCnameRegex = """([a-zA-Z@-_]*\s+)(\d+\s+)CNAME\s+\d+(\s+.*)""".r
    line match {
      case DynCnameRegex(name, ttl, resourceRecord) => s"$name${ttl}CNAME$resourceRecord"
      case other => other
    }
  }

  def loadBindFile(bindFile: File): InputStream = {
    val bindContents = Source.fromFile(bindFile, "ASCII")
    val bindLines = bindContents.getLines
    val cleanedLines = bindLines.map(cleanDynCname)
    new ByteArrayInputStream(cleanedLines.mkString("\n").getBytes("ASCII"))
  }

  def parseBindData(bindData: InputStream): List[Record] = {
    val bindFileParser = new Master(bindData)
    val jRecords = new MasterIterator(bindFileParser).toList
    jRecords.map(Record.apply)
  }
}