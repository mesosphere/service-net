package mesosphere.servicenet.http.json

import mesosphere.servicenet.dsl._
import mesosphere.servicenet.util.{ InetAddressHelper, Spec }
import play.api.libs.json._
import play.api.libs.functional.syntax._
import java.net.{ InetAddress, Inet4Address, Inet6Address }

class DocProtocolSpec extends Spec {

  object Fixture {
    val inet4Address = InetAddressHelper.ipv4("192.168.1.115")

    val inet6Address =
      InetAddressHelper.ipv6("fc75:0000:0000:0000:0000:9fb2:0000:0804")

    val inet6Subnet =
      Inet6Subnet(addr = inet6Address, prefixBits = 64)

    val interface = Interface(name = "my-service", addrs = Seq(inet6Address))

    val aaaa = AAAA(
      label = "foo.bar",
      addrs = Seq(inet6Address)
    )

    val natFan = NATFan(
      name = "my-nat",
      entrypoint = inet6Address,
      endpoints = Seq(inet6Address, inet6Address)
    )

    val tunnel = Tunnel6in4(
      name = "my-tunnel",
      localEnd = inet4Address,
      remoteEnd = inet4Address,
      addr = inet6Address,
      remoteIPv6Net = inet6Subnet
    )

    val doc = Doc(
      interfaces = Seq(interface),
      dns = Seq(aaaa),
      natFans = Seq(natFan),
      tunnels = Seq(tunnel)
    )

    val diff = Diff(
      interfaces = Seq(Add(interface), Remove(interface)),
      dns = Seq(Add(aaaa), Remove(aaaa)),
      natFans = Seq(Add(natFan), Remove(natFan)),
      tunnels = Seq(Add(tunnel), Remove(tunnel))
    )

  }

  import DocProtocol._

  "DocProtocol" should "read and write Inet4Address" in {
    import Fixture._
    val json = Json.toJson(inet4Address)
    json should equal (JsString("192.168.1.115"))

    val readResult = json.as[Inet4Address]
    readResult should equal (inet4Address)

    intercept[JsResultException] {
      JsString("one fish two fish").as[Inet4Address]
    }

    intercept[JsResultException] {
      JsString("FC75:0000:0000:0000:0000:9FB2:0000:0804").as[Inet4Address]
    }

    intercept[JsResultException] {
      JsArray(Seq(JsString("one"), JsString("two"))).as[Inet4Address]
    }
  }

  it should "read and write Inet6Address" in {
    import Fixture._
    val json = Json.toJson(inet6Address)
    json should equal (JsString("fc75:0:0:0:0:9fb2:0:804"))

    val readResult = json.as[Inet6Address]
    readResult should equal (inet6Address)

    intercept[JsResultException] {
      JsString("one fish two fish").as[Inet6Address]
    }

    intercept[JsResultException] {
      JsString("192.168.1.115").as[Inet6Address]
    }

    intercept[JsResultException] {
      JsArray(Seq(JsString("one"), JsString("two"))).as[Inet6Address]
    }
  }

  it should "read and write Inet6Subnet" in {
    import Fixture._
    val json = Json.toJson(inet6Subnet)
    json should equal (JsString("fc75:0:0:0:0:9fb2:0:804/64"))

    val readResult = json.as[Inet6Subnet]
    readResult should equal (inet6Subnet)
  }

  it should "read and write Interface" in {
    import Fixture._
    val json = Json.toJson(interface)
    json should equal (Json.obj(
      "name" -> "my-service",
      "addrs" -> Json.arr(inet6Address)
    ))

    val readResult = json.as[Interface]
    readResult should equal (interface)
  }

  it should "read and write DNS" in {
    import Fixture._
    val json = Json.toJson(aaaa)
    json should equal (Json.obj(
      "label" -> "foo.bar",
      "addrs" -> Json.toJson(Seq(inet6Address)),
      "localize" -> false
    ))

    val readResult = json.as[DNS]
    readResult should equal (aaaa)
  }

  it should "read and write NATFan" in {
    import Fixture._
    val json = Json.toJson(natFan)
    json should equal (Json.obj(
      "name" -> "my-nat",
      "entrypoint" -> Json.toJson(inet6Address),
      "endpoints" -> Json.toJson(Seq(inet6Address, inet6Address))
    ))

    val readResult = json.as[NATFan]
    readResult should equal (natFan)
  }

  it should "read and write Tunnel" in {
    import Fixture._
    val json = Json.toJson(tunnel)
    json should equal (Json.obj(
      "name" -> "my-tunnel",
      "localEnd" -> Json.toJson(inet4Address),
      "remoteEnd" -> Json.toJson(inet4Address),
      "addr" -> Json.toJson(inet6Address),
      "remoteIPv6Net" -> Json.toJson(inet6Subnet)
    ))

    val readResult = json.as[Tunnel]
    readResult should equal (tunnel)
  }

  it should "read and write Doc" in {
    import Fixture._
    val json = Json.toJson(doc)
    json should equal (Json.obj(
      "interfaces" -> Json.toJson(Seq(interface)),
      "dns" -> Json.toJson(Seq(aaaa)),
      "natFans" -> Json.toJson(Seq(natFan)),
      "tunnels" -> Json.toJson(Seq(tunnel))
    ))

    val readResult = json.as[Doc]
    readResult should equal (doc)
  }

  it should "read and write Diff" in {
    import Fixture._
    val json = Json.toJson(diff)

    val readResult = json.as[Diff]
    readResult should equal (diff)
  }
}
