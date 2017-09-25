package com.karasiq.gdrive.files

import java.io.{InputStream, OutputStream}
import java.util.UUID

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

import com.google.api.client.util.IOUtils
import com.google.api.services.drive.{Drive, DriveRequest}
import com.google.api.services.drive.model.{File, FileList}

import com.karasiq.gdrive.context.GDriveContext
import com.karasiq.gdrive.oauth.GDriveSession
import com.karasiq.gdrive.query.GDriveUtils

object GDriveService {
  def apply(applicationName: String)(implicit context: GDriveContext, session: GDriveSession): GDriveService = {
    new GDriveService(applicationName)
  }
}

class GDriveService(applicationName: String)(implicit context: GDriveContext, session: GDriveSession) {
  // -----------------------------------------------------------------------
  // Aliases
  // -----------------------------------------------------------------------
  final type EntityId = String
  final type EntityPath = Seq[EntityId]
  final type EntityList = Seq[GDrive.Entity]

  import implicits._

  import com.karasiq.gdrive.query.{GDriveQueries ⇒ Q}
  import com.karasiq.gdrive.query.GApiQuery.DSL._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  val driveService = new Drive.Builder(context.transport, context.jsonFactory, session.credential)
    .setApplicationName(applicationName)
    .build()

  // -----------------------------------------------------------------------
  // Quota
  // -----------------------------------------------------------------------
  def quota(): GDrive.Quota = {
    driveService.about().get()
      .setFields(GDrive.Quota.fields)
      .execute()
  }

  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
  def folders(): EntityList = {
    fileQuery(Q.isFolder && Q.nonTrashed)
  }

  def folders(parentId: EntityId): EntityList = {
    fileQuery(Q.isFolder && Q.nonTrashed && Q.parent(parentId))
  }

  def folder(folderId: EntityId): GDrive.Entity = {
    this.file(folderId)
  }

  def folder(path: EntityPath): GDrive.Entity = {
    path.foldLeft(GDriveUtils.RootEntity) { (parent, name) ⇒
      folder(parent.id, name).getOrElse(throw new NoSuchElementException(name))
    }
  }

  def createFolder(path: EntityPath): GDrive.Entity = {
    path.foldLeft(GDriveUtils.RootEntity) { (parent, name) ⇒
      folder(parent.id, name).getOrElse(createFolder(parent.id, name))
    }
  }

  def folder(parentId: EntityId, name: String) = {
    fileQuery(Q.isFolder && Q.nonTrashed && Q.parent(parentId) && Q.name(name)).headOption
  }

  def createFolder(parentId: EntityId, name: String): GDrive.Entity = {
    val file = new File()
      .setName(name)
      .setMimeType(GDriveUtils.FolderMime)
      .setParents(Seq(parentId).asJava)

    driveService.files().create(file).toEntity
  }

  def traverseFolder(path: EntityPath): Map[EntityPath, EntityList] = {
    def traverseFolderRec(path: EntityPath, folder: GDrive.Entity): Iterator[(EntityPath, EntityList)] = {
      val files = this.files(folder.id)
      def subFolders() = this.folders(folder.id)
      Iterator.single(path → files) ++ subFolders().iterator.flatMap(folder ⇒ traverseFolderRec(path :+ folder.name, folder))
    }

    traverseFolderRec(path, this.folder(path)).toMap
  }

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def files: EntityList = {
    driveService.files().list().toEntityList
  }

  def fileQuery(query: String): EntityList = {
    driveService.files().list().setQ(query).toEntityList
  }

  def files(parentId: EntityId): EntityList = {
    fileQuery(Q.isFile && Q.nonTrashed && Q.parent(parentId))
  }

  def files(parentId: EntityId, name: String): EntityList = {
    fileQuery(Q.isFile && Q.nonTrashed && Q.parent(parentId) && Q.name(name))
  }

  def file(id: EntityId): GDrive.Entity = {
    driveService.files().get(id).toEntity
  }

  def fileExists(parentId: EntityId, name: String): Boolean = {
    files(parentId, name).nonEmpty
  }

  def delete(fileId: EntityId): Unit = {
    driveService.files()
      .delete(fileId)
      .execute()
  }

  // -----------------------------------------------------------------------
  // Upload/download
  // -----------------------------------------------------------------------
  def upload(parentId: EntityId, name: String, content: GDriveContent): GDrive.Entity = {
    def tryDeleteTempFile(name: String): Unit = {
      val tempFile = Try(fileQuery(Q.isFile && Q.name(name))) getOrElse Nil
      tempFile.foreach(f ⇒ Try(delete(f.id)))
    }

    val tempFileName = name + "_" + UUID.randomUUID() + ".tmp"
    val fileMetadata = new File()
      .setName(tempFileName)
      .setParents(Seq(parentId).asJava)

    try {
      val request = driveService.files()
        .create(fileMetadata, content)
        .setDisableGZipContent(true)

      //noinspection ConvertExpressionToSAM
      request.getMediaHttpUploader
        // .setSleeper(new Sleeper { def sleep(millis: Long): Unit = () })
        .setDirectUploadEnabled(true)
        .setDisableGZipContent(true)

      val result = concurrent.blocking(request.toEntity)

      driveService.files()
        .update(result.id, new File().setName(name))
        .toEntity
    } catch { case NonFatal(error) ⇒
      tryDeleteTempFile(tempFileName)
      throw error
    }
  }

  def download(fileId: EntityId): InputStream = {
    val request = driveService.files().get(fileId)

    request.setDisableGZipContent(true)
    request.getMediaHttpDownloader.setDirectDownloadEnabled(true)

    request.executeMediaAsInputStream()
  }

  def download(fileId: EntityId, outputStream: OutputStream): Unit = {
    val inputStream = download(fileId)
    concurrent.blocking(IOUtils.copy(inputStream, outputStream))
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  object implicits {
    implicit class FileRequestOps(request: DriveRequest[File]) {
      def toEntity: GDrive.Entity = {
        request.setFields(GDrive.Entity.fields)
          .execute()
      }
    }

    implicit class FileListRequestOps(request: DriveRequest[FileList]) {
      def toEntityList: EntityList = {
        request.setFields(GDrive.Entity.listFields)
          .execute()
          .toEntityList
      }
    }

    implicit class FileListOps(fl: FileList) {
      def toEntityList: EntityList = {
        fl.getFiles.asScala.map(GDrive.Entity.fromFile)
      }
    }
  }
}
