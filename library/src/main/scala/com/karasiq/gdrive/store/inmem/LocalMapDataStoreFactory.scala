package com.karasiq.gdrive.store.inmem

import java.io

import scala.collection.concurrent.TrieMap

import com.google.api.client.util.store.{AbstractDataStoreFactory, DataStore}

private[store] trait LocalMapDataStoreFactory extends AbstractDataStoreFactory {
  def createDataStore[V <: io.Serializable](id: String): DataStore[V] = {
    new ScalaMapDataStore(this, id, TrieMap.empty[String, V])
  }
}
