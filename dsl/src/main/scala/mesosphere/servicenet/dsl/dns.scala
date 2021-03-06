package mesosphere.servicenet.dsl

import java.net.Inet6Address

//////////////////////////////////////////////////////////////////////////////
//  DNS Record Types  ////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

/**
  * @param localize Indicates that the DNS server should use local
  *                 information, like the instance subnet, to filter the
  *                 records before returning them.
  */
case class AAAA(label: String,
                addrs: Seq[Inet6Address],
                localize: Boolean = false) extends DNS {
  val data: Seq[String] = addrs.map(_.getHostAddress)
}

///**
//  * SRV record
//  */
//case class SRV(label: String, endpoints: Seq[SRVData] = Seq()) extends DNS {
//  val data: Seq[String] = for (srv <- endpoints) yield {
//    import srv._
//    s"$priority $weight $port $target"
//  }
//
//  /**
//    * Clients should never cache SRV records but TTL of 0 is not to be used.
//    *
//    * http://mark.lindsey.name/2009/03/never-use-dns-ttl-of-zero-0.html
//    */
//  override val ttl: Int = 1
//}
//
//case class SRVData(target: String,
//                   port: Int = 0,
//                   weight: Int = 1,
//                   priority: Int = 1)

/**
  * See http://www.zytrax.com/books/dns/ch8/#generic
  */
sealed trait DNS extends NetworkEntity {
  val label: String
  val recordType: String = getClass.getSimpleName.toUpperCase()
  val recordClass: String = "IN" // Internet class records are the norm
  val ttl: Int = 3600 // 1 hour
  val data: Seq[String]
  lazy val records: Seq[(String, String, String, String, String)] =
    for (d <- data) yield (label, ttl.toString, recordClass, recordType, d)
  val name: String = label
}
