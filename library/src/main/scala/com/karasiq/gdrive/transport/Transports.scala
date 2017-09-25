package com.karasiq.gdrive.transport

import java.net.{InetSocketAddress, Proxy}

import scala.util.Try

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.apache.ApacheHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.typesafe.config.Config
import org.apache.http.HttpHost

import com.karasiq.common.configs.ConfigImplicits._

object Transports {
  def fromConfig(config: Config): HttpTransport = {
    def createProxy[T](f: Config ⇒ T): T = {
      val proxyConfig = config.getConfigIfExists("proxy")
      Try(f(proxyConfig)).getOrElse(null.asInstanceOf[T])
    }

    config.optional(_.getString("transport-type").toLowerCase) match {
      case Some("net") | Some("java.net") ⇒
        new NetHttpTransport.Builder()
          .setProxy(createProxy(Proxies.javaNetProxy))
          .build()

      case Some("apache") | Some("apache-http") | None ⇒
        new ApacheHttpTransport.Builder()
          .setProxy(createProxy(Proxies.apacheProxy))
          .build()

      case Some(other) ⇒
        throw new IllegalArgumentException(other)
    }
  }

  private[this] object Proxies {
    def javaNetProxy(pc: Config) = {
      val proxyType = pc.optional(_.getString("type").toLowerCase) match {
        case Some("http" | "https") | None ⇒
          Proxy.Type.HTTP

        case Some("socks" | "socks4" | "socks4a" | "socks5") ⇒
          Proxy.Type.SOCKS

        case Some(other) ⇒
          throw new IllegalArgumentException(other)
      }

      new Proxy(proxyType, InetSocketAddress.createUnresolved(pc.getString("host"), pc.getInt("port")))
    }

    def apacheProxy(pc: Config) = {
      new HttpHost(pc.getString("host"), pc.getInt("port"))
    }
  }
}
