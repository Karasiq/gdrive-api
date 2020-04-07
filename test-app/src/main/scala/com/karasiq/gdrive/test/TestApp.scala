package com.karasiq.gdrive.test

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.Paths

import akka.util.ByteString
import com.karasiq.common.memory.MemorySize
import com.karasiq.gdrive.context.GDriveContext
import com.karasiq.gdrive.files.GDriveService
import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.gdrive.oauth.GDriveOAuth
import com.karasiq.gdrive.store.DataStores
import com.typesafe.config.ConfigFactory

import scala.io.Source

object TestApp extends App {
  val config = ConfigFactory.parseFile(new File("gdrive-test.conf")).getConfig("gdrive")
  implicit val context = GDriveContext(config, DataStores.files(Paths.get("test-sessions")))

  val oauth = GDriveOAuth()
  implicit val session = oauth.authorize(config.getString("user"))

  val service = GDriveService("gdrive-test")
  val quota = service.quota()
  println(MemorySize(quota.usedSize) + " of " + MemorySize(quota.totalSize) + " (max: " + MemorySize(quota.maxUploadSize) + ")")

  println(service.teamDrives())
  implicit val testDriveId = TeamDriveId(service.teamDrives().headOption.fold(null: String)(_.getId))
  val folder = service.createFolder(Seq("gdrive", "test"))
  println(folder)

  if (service.fileExists(folder.id, "test.txt")) {
    println("File exists")
    service.filesInWithName(folder.id, "test.txt").foreach { f ⇒
      val outputStream = new ByteArrayOutputStream()
      service.download(f.id, outputStream)
      service.delete(f.id)
      println(ByteString(outputStream.toByteArray).utf8String)
    }
  }
  println(service.filesIn(folder.id).toVector)
  println(service.upload(folder.id, "test.txt", Source.fromFile("LICENSE").getLines().mkString("\n")))

  service.traverseFolder(Nil).foreach(println)
}
