package mesosphere.servicenet.util

import java.net.{ InetAddress, Inet4Address, Inet6Address }

object InetAddressHelper {

  /**
    * Returns a canonical `java.net.Inet6Address` for the supplied address
    * string.
    *
    * @param addr A well-formed IPv6 Address
    */
  @throws[java.net.UnknownHostException]
  def ipv6(addr: String): Inet6Address =
    InetAddress.getByName(addr) match {
      case inet: Inet4Address =>
        throw new IllegalArgumentException("An IPv4 address was supplied")
      case inet: Inet6Address => inet
    }

  /**
    * Returns a canonical `java.net.Inet4Address` for the supplied address
    * string.
    *
    * @param addr A well-formed IPv4 Address
    */
  @throws[java.net.UnknownHostException]
  def ipv4(addr: String): Inet4Address =
    InetAddress.getByName(addr) match {
      case inet: Inet4Address => inet
      case inet: Inet6Address =>
        throw new IllegalArgumentException("An IPv6 address was supplied")
    }

  /**
    * Calculate the 6to4 address of an IPv4 address.
    */
  def ipv6(ipv4: Inet4Address): Inet6Address = {
    val Array(a, b, c, d) = ipv4.getAddress
    ipv6(f"2002:$a%02x$b%02x:$c%02x$d%02x::")
  }

  /**
    * Present and IPv6 address in an ASCIIbetically sortable form, with all
    * zeroes filled in. `1511::f:42` becomes
    * `1511:0000:0000:0000:0000:0000:000f:0042`.
    */
  def fullLengthIPv6(addr: Inet6Address): String = addr.getAddress
    .map(b => f"$b%02x").grouped(2).map(_.mkString("")).mkString(":")

  def arpa(ipv6: Inet6Address): String = {
    ipv6.getAddress().map(b => f"$b%02x".toCharArray)
      .flatten.reverse.mkString(".") + ".ip6.arpa"
  }

  def arpa(ipv4: Inet4Address): String =
    ipv4.getHostAddress.split('.').reverse.mkString(".") + ".in-addr.arpa"

  def next(addr: Inet6Address): Inet6Address = {
    val incremented = incrementByteArray(addr.getAddress)
    InetAddress.getByAddress(incremented).asInstanceOf[Inet6Address]
  }

  private def incrementByteArray(bytes: Array[Byte]): Array[Byte] = {
    var carry: Boolean = true
    // Big-endian array of machine-endian bytes
    for (b <- bytes.reverse) yield {
      val b_ = if (carry) (b + 1).toByte else b
      carry = carry && b_ == 0
      b_
    }
  }.reverse
}
