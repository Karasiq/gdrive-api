package com.karasiq.gdrive.store

import java.nio.file.Path

import scala.collection.concurrent.TrieMap

import com.google.api.client.util.store.FileDataStoreFactory

import com.karasiq.gdrive.store.inmem.{GlobalMapDataStoreFactory, LocalMapDataStoreFactory}

object DataStores {
  object GlobalMap extends GlobalMapDataStoreFactory {
    val globalMap = TrieMap.empty
  }

  object LocalMap extends LocalMapDataStoreFactory

  def files(path: Path): FileDataStoreFactory = {
    new FileDataStoreFactory(path.toFile)
  }
}
