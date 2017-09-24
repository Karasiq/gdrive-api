package com.karasiq.gdrive.context

import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.DataStoreFactory
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.gdrive.store.DataStores
import com.karasiq.gdrive.transport.Transports

trait GDriveContext {
  def config: Config
  def jsonFactory: JsonFactory
  def transport: HttpTransport
  def dataStore: DataStoreFactory
}

object GDriveContext {
  def apply(_config: Config, _dataStore: DataStoreFactory = DataStores.LocalMap): GDriveContext = new GDriveContext {
    val config = _config
    val jsonFactory = JacksonFactory.getDefaultInstance
    val transport = Transports.fromConfig(config.getConfigIfExists("transport"))
    val dataStore = _dataStore
  }
}