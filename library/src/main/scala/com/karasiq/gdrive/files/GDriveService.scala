package com.karasiq.gdrive.files

import java.io.{InputStream, OutputStream}

import scala.collection.JavaConverters._

import com.google.api.client.http.InputStreamContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.{File, FileList}

import com.karasiq.gdrive.context.GDriveContext
import com.karasiq.gdrive.oauth.GDriveSession

object GDriveService {
  def apply(applicationName: String)(implicit context: GDriveContext, session: GDriveSession): GDriveService = {
    new GDriveService(applicationName)
  }
}

class GDriveService(applicationName: String)(implicit context: GDriveContext, session: GDriveSession) {
  val driveService = new Drive.Builder(context.transport, context.jsonFactory, session.credential)
    .setApplicationName(applicationName)
    .build()

  def quota(): GDrive.Quota = {
    driveService.about().get()
      .setFields(GDrive.Quota.fields)
      .execute()
  }

  def folders(): Seq[GDrive.Entity] = {
    driveService.files()
      .list()
      .setQ("mimeType = 'application/vnd.google-apps.folder'")
      .execute()
      .asScala
  }

  def folders(parentId: String): Seq[GDrive.Entity] = {
    driveService.files().list()
      .setQ(s"mimeType = 'application/vnd.google-apps.folder' and '${escapeQuery(parentId)}' in parents")
      .setFields(GDrive.Entity.listFields)
      .execute()
      .asScala
  }

  def folder(id: String): GDrive.Entity = {
    this.file(id)
  }

  def folder(path: Seq[String]): GDrive.Entity = {
    def getFolder(parentId: String, name: String) = {
      driveService.files()
        .list()
        .setQ(s"mimeType = 'application/vnd.google-apps.folder' and name='${escapeQuery(name)}' and '${escapeQuery(parentId)}' in parents")
        .setFields(GDrive.Entity.listFields)
        .execute()
        .asScala
        .headOption
    }

    def createFolder(parentId: String, name: String): GDrive.Entity = {
      val file = new File()
        .setName(name)
        .setMimeType("application/vnd.google-apps.folder")
        .setParents(Seq(parentId).asJava)

      driveService.files().create(file)
        .setFields(GDrive.Entity.fields)
        .execute()
    }

    val rootEntity = GDrive.Entity("root", "", Nil)
    path.foldLeft(rootEntity) { (parent, name) ⇒
      getFolder(parent.id, name)
        .getOrElse(createFolder(parent.id, name))
    }
  }

  def traverseFolder(path: Seq[String]): Map[Seq[String], Seq[GDrive.Entity]] = {
    def traverseFolderRec(path: Seq[String], folder: GDrive.Entity): Iterator[(Seq[String], Seq[GDrive.Entity])] = {
      val files = this.files(folder.id)
      def subFolders() = this.folders(folder.id)
      Iterator.single(path → files) ++ subFolders().iterator.flatMap(folder ⇒ traverseFolderRec(path :+ folder.name, folder))
    }

    traverseFolderRec(path, this.folder(path)).toMap
  }

  def files: Seq[GDrive.Entity] = {
    driveService.files().list()
      .setFields(GDrive.Entity.listFields)
      .execute()
      .asScala
  }

  def files(parentId: String): Seq[GDrive.Entity] = {
    driveService.files().list()
      .setQ(s"mimeType != 'application/vnd.google-apps.folder' and '${escapeQuery(parentId)}' in parents")
      .setFields(GDrive.Entity.listFields)
      .execute()
      .asScala
  }

  def files(parentId: String, name: String): Seq[GDrive.Entity] = {
    driveService.files().list()
      .setQ(s"mimeType != 'application/vnd.google-apps.folder' and '${escapeQuery(parentId)}' in parents and name = '${escapeQuery(name)}'")
      .setFields(GDrive.Entity.listFields)
      .execute()
      .asScala
  }

  def file(id: String): GDrive.Entity = {
    driveService.files().get(id)
      .setFields(GDrive.Entity.fields)
      .execute()
  }

  def fileExists(parentId: String, name: String): Boolean = {
    files(parentId, name).nonEmpty
  }

  def delete(id: String): Unit = {
    driveService.files().delete(id)
      .execute()
  }

  def upload(parentId: String, name: String, inputStream: InputStream): GDrive.Entity = {
    val fileMetadata = new File()
      .setParents(Seq(parentId).asJava)
      .setName(name)

    val content = new InputStreamContent("application/octet-stream", inputStream)
    driveService.files()
      .create(fileMetadata, content)
      .setFields(GDrive.Entity.fields)
      .execute()
  }

  def download(id: String, outputStream: OutputStream): Unit = {
    driveService.files().get(id)
      .executeMediaAndDownloadTo(outputStream)
  }

  private[this] def escapeQuery(name: String): String = {
    name.replaceAllLiterally("'", "\\'")
  }

  private implicit class FileListOps(fl: FileList) {
    def asScala: Seq[GDrive.Entity] = {
      fl.getFiles.asScala.map(GDrive.Entity.fromFile)
    }
  }
}
