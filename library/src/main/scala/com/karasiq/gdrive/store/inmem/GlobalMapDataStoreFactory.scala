package com.karasiq.gdrive.store.inmem

import java.io

import scala.collection.mutable
import scala.collection.concurrent.TrieMap

import com.google.api.client.util.store.AbstractDataStoreFactory

private[store] trait GlobalMapDataStoreFactory extends AbstractDataStoreFactory {
  val globalMap: mutable.Map[String, mutable.Map[String, Any]]

  def createDataStore[V <: io.Serializable](id: String) = {
    val map = globalMap.getOrElseUpdate(id, TrieMap.empty[String, Any])
    new ScalaMapDataStore[V](GlobalMapDataStoreFactory.this, id, map.asInstanceOf[TrieMap[String, V]])
  }
}
