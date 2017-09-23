package com.karasiq.gdrive.store.inmem

import java.io

import scala.collection.mutable
import scala.collection.JavaConverters._

import com.google.api.client.util.store.{AbstractDataStore, DataStoreFactory}

private[store] final class ScalaMapDataStore[V <: io.Serializable](dataStoreFactory: DataStoreFactory, id: String, map: mutable.Map[String, V])
  extends AbstractDataStore[V](dataStoreFactory, id) {

  def values() = {
    map.values.asJavaCollection
  }

  def delete(key: String) = {
    map -= key
    this
  }

  def get(key: String) = {
    map.getOrElse(key, null.asInstanceOf[V])
  }

  def keySet() = {
    map.keySet.asJava
  }

  def set(key: String, value: V) = {
    map += key → value
    this
  }

  def clear() = {
    map.clear()
    this
  }
}
