package com.karasiq.gdrive.transport

import java.net.{InetSocketAddress, Proxy}

import scala.collection.JavaConverters._
import scala.util.Try

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.apache.ApacheHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.typesafe.config.Config
import org.apache.http.HttpHost
import org.apache.http.conn.params.{ConnManagerPNames, ConnPerRouteBean}
import org.apache.http.params.HttpParams

import com.karasiq.common.configs.ConfigImplicits._

object Transports {
  def fromConfig(config: Config): HttpTransport = {
    def createProxy[T](f: Config ⇒ T): T = {
      val proxyConfig = config.getConfigIfExists("proxy")
      Try(f(proxyConfig)).getOrElse(null.asInstanceOf[T])
    }

    config.optional(_.getString("transport-type").toLowerCase) match {
      case Some("apache") | Some("apache-http") | None ⇒
        val builder = new ApacheHttpTransport.Builder()
          .setProxy(createProxy(Proxies.apacheProxy))
        Configs.applyApacheHttpParams(builder.getHttpParams, config.getConfigIfExists("http-parameters"))
        builder.build()

      case Some("net") | Some("java.net") ⇒
        new NetHttpTransport.Builder()
          .setProxy(createProxy(Proxies.javaNetProxy))
          .build()

      case Some(other) ⇒
        throw new IllegalArgumentException(other)
    }
  }

  private[this] object Configs {
    // org.apache.http.params.CoreProtocolPNames
    // org.apache.http.conn.params.ConnManagerPNames
    def applyApacheHttpParams(p: HttpParams, config: Config): Unit = {
      config.root().asScala.foreach { case (key, value) ⇒
        if (key == ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE) p.setParameter(key, new ConnPerRouteBean(value.unwrapped().asInstanceOf[Int]))
        else p.setParameter(key, value.unwrapped())
      }
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
