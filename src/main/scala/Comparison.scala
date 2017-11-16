import java.io.{ByteArrayOutputStream, File, PrintStream, StringWriter}
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.JsonBodyReadables._

import scala.concurrent.Future
import cats.implicits._

case class Config(zonefiles: List[File] = Nil, ns1Key: Option[String] = None)

object Comparison {
  import scala.concurrent.ExecutionContext.Implicits._

  val parser = new scopt.OptionParser[Config]("dns-comparison") {
    head("dns-comparison", "1.0")
    opt[String]("ns1Key").required().action( (f, c) => c.copy(ns1Key = Some(f)))
    arg[File]("file").minOccurs(1).maxOccurs(1024).action( (f, c) => c.copy(zonefiles = c.zonefiles :+ f))
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()) match {
      case Some(Config(zonefiles, Some(ns1Key))) =>
        implicit val system = ActorSystem()
        implicit val materializer = ActorMaterializer()
        val wsClient = StandaloneAhcWSClient()
        zonefiles.traverseU { zonefile =>
          compareZone(wsClient, zonefile, ns1Key)
        }.map{ _.flatten }
        .andThen { case _ =>
          System.err.println("Closing down client")
            wsClient.close()
        }
        .andThen { case _ =>
          System.err.println("Terminating actor system")
          system.terminate()
        }.foreach { results =>
          results.foreach { result =>
            System.err.println(result)
          }
        }
      case _ =>
    }
  }

  def compareZone(wsClient: StandaloneWSClient, bindFile: File, ns1Key: String): Future[Option[String]] = {
    val inputStream = Dyn.loadBindFile(bindFile)
    val records = Dyn.parseBindData(inputStream)

    val maybeZone = records.find(_.typeName == "SOA").map(_.name)

    maybeZone.map { zone =>
      getNs1Zone(wsClient, zone, ns1Key)
        .map { jsValue =>
          parseNs1Zone(jsValue).map { ns1Zone =>
            compare(ns1Zone, records)
          }
        }
    }.getOrElse(Future.successful(None))
  }

  def getNs1Zone(wsClient: StandaloneWSClient, zone: String, key: String): Future[JsValue] = {
    wsClient.url(s"https://api.nsone.net/v1/zones/$zone").withHttpHeaders("X-NSONE-Key" -> key).get().map { response ⇒
      val statusText: String = response.statusText
      val body = response.body[JsValue]
      //println(s"Got a response $statusText")
      body
    }
  }

  def parseNs1Zone(jsValue: JsValue): Option[Ns1Zone] = {
    Json.fromJson[Ns1Zone](jsValue).asOpt
  }

  def compare(ns1Zone: Ns1Zone, rawDynRecords: List[Record]): String = {
    val baos = new ByteArrayOutputStream()
    val printStream = new PrintStream(baos)

    val ns1Records = ns1Zone.records.flatMap(_.asDnsRecords).sortBy(Record.unapply)
    val dynRecords = rawDynRecords.filterNot(_.typeName=="SOA").sortBy(Record.unapply)

    printStream.println(s"\n=== Results for ${ns1Zone.zone} ===")

    // check each record in turn
    if (ns1Records == dynRecords) {
      printStream.println(s"\nAll ${ns1Records.size} records match")
    } else {
      // check number of the entries
      val ns1Entries = ns1Records.map(_.name).toSet
      val dynEntries = dynRecords.map(_.name).toSet
      if (ns1Entries.size != dynEntries.size) {
        printStream.println(s"\nNumber of records different for NS1/Dyn NS1: ${ns1Entries.size} Dyn: ${dynEntries.size}")
        printStream.println(s"\nIn NS1 but not in Dyn: \n  ${(ns1Entries -- dynEntries).toList.sorted.mkString("\n  ")}")
        printStream.println(s"\nIn Dyn but not in NS1: \n  ${(dynEntries -- ns1Entries).toList.sorted.mkString("\n  ")}")
      }

      // check each set of records for duplicates
      if (ns1Records.size != ns1Records.toSet.size) printStream.println("\nDuplicates found in NS1")
      if (dynRecords.size != dynRecords.toSet.size) printStream.println("\nDuplicates found in Dyn")

      // output the unique records in each set
      val dynOnly = (dynRecords.toSet -- ns1Records.toSet).toList.sortBy(Record.unapply)
      val ns1Only = (ns1Records.toSet -- dynRecords.toSet).toList.sortBy(Record.unapply)
      if (dynOnly.nonEmpty) printStream.println(s"\nFound records that only exist in Dyn: \n  ${dynOnly.mkString("\n  ")}")
      if (ns1Only.nonEmpty) printStream.println(s"\nFound records that only exist in NS1: \n  ${ns1Only.mkString("\n  ")}")
    }
    new String(baos.toByteArray, StandardCharsets.UTF_8)
  }
}

