import play.api.libs.json.Json

case class Ns1Record(
                      domain: String,
                      ttl: Long,
                      `type`: String,
                      short_answers: List[String]
                    ) {
  override def toString = {
    val prefix = s"$domain $ttl ${`type`} "
    short_answers.map { answer =>
      s"$prefix $answer"
    }.mkString("\n")
  }
  def asDnsRecords = short_answers.map { answer =>
    Record(domain, ttl, `type`, answer.stripSuffix("."))
  }
}

object Ns1Record {
  implicit val reads = Json.reads[Ns1Record]
}

case class Ns1Zone(
                    nx_ttl: Long,
                    retry: Long,
                    zone: String,
                    network_pools: List[String],
                    refresh: Long,
                    expiry: Long,
                    dns_servers: List[String],
                    records: List[Ns1Record]
                  ) {
  override def toString = s"$zone $refresh $retry $expiry $nx_ttl\n${records.mkString("\n")}"

}

object Ns1Zone {
  implicit val reads = Json.reads[Ns1Zone]
}