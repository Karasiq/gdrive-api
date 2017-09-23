package com.karasiq.gdrive.transport

import scala.util.Try

import com.google.api.client.http.apache.ApacheHttpTransport
import com.typesafe.config.Config
import org.apache.http.HttpHost

import com.karasiq.common.configs.ConfigImplicits._

object Transports {
  def fromConfig(config: Config): ApacheHttpTransport = {
    val proxyOpt = config.optional(_.getConfig("proxy"))
      .flatMap(pc ⇒ Try(new HttpHost(pc.getString("host"), pc.getInt("port"))).toOption)

    new ApacheHttpTransport.Builder()
      .setProxy(proxyOpt.orNull)
      .build()
  }
}
