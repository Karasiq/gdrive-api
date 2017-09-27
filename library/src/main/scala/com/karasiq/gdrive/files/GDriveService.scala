package com.karasiq.gdrive.files

import java.io.{InputStream, OutputStream}
import java.util.UUID

import scala.collection.JavaConverters._
import scala.util.Try

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
  final type EntityStream = Iterator[GDrive.Entity]

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
  def folders(): EntityStream = {
    fileQuery(Q.isFolder && Q.nonTrashed)
  }

  def folders(parentId: EntityId): EntityStream = {
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
    fileQuery(Q.isFolder && Q.nonTrashed && Q.parent(parentId) && Q.name(name))
      .take(1)
      .toSeq
      .headOption
  }

  def createFolder(parentId: EntityId, name: String): GDrive.Entity = {
    val file = new File()
      .setName(name)
      .setMimeType(GDriveUtils.FolderMime)
      .setParents(Seq(parentId).asJava)

    driveService.files().create(file).toEntity
  }

  def traverseFolder(path: EntityPath): Iterator[(EntityPath, GDrive.Entity)] = {
    def traverseFolderRec(path: EntityPath, folderId: EntityId): Iterator[(EntityPath, GDrive.Entity)] = {
      def files() = this.files(folderId)
      def subFolders() = this.folders(folderId)

      files().map((path, _)) ++ subFolders().flatMap(folder ⇒ traverseFolderRec(path :+ folder.name, folder.id))
    }

    traverseFolderRec(path, this.folder(path).id)
  }

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def files: EntityStream = {
    driveService.files().list().toEntityStream
  }

  def fileQuery(query: String): EntityStream = {
    driveService.files().list()
      .setOrderBy("createdTime")
      .setQ(query)
      .toEntityStream
  }

  def files(parentId: EntityId): EntityStream = {
    fileQuery(Q.isFile && Q.nonTrashed && Q.parent(parentId))
  }

  def files(parentId: EntityId, name: String): EntityStream = {
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
    def deleteTempFile(id: EntityId): Unit = {
      Try(driveService.files().delete(id).execute())
    }

    val tempFileName = name + "_" + UUID.randomUUID() + ".tmp"
    val fileMetadata = new File().setName(tempFileName)
    
    val request = driveService.files()
      .create(fileMetadata, content)
      .setDisableGZipContent(true)

    //noinspection ConvertExpressionToSAM
    request.getMediaHttpUploader
      // .setSleeper(new Sleeper { def sleep(millis: Long): Unit = () })
      .setDirectUploadEnabled(true)
      .setDisableGZipContent(true)

    val result = concurrent.blocking(request.toEntity)

    try {
      driveService.files()
        .copy(result.id, new File().setParents(Seq(parentId).asJava).setName(name))
        .toEntity
    } finally {
      deleteTempFile(result.id)
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

    implicit class GenFileListRequestOps(request: DriveRequest[FileList]) {
      def toEntityList: EntityList = {
        request.setFields(GDrive.Entity.listFields)
          .execute()
          .toEntityList
      }
    }

    implicit class FileListRequestOps(request: Drive#Files#List) {
      def toEntityStream: EntityStream = {
        def toEntityStreamRec(request: Drive#Files#List): EntityStream = {
          val result = request.execute()
          val resultList = result.toEntityList
          resultList.toIterator ++ Option(result.getNextPageToken)
            .iterator
            .flatMap(token ⇒ toEntityStreamRec(request.setPageToken(token)))
        }
        toEntityStreamRec(request
          .setPageSize(1000)
          .setFields("nextPageToken, " + GDrive.Entity.listFields))
      }
    }

    implicit class FileListOps(fl: FileList) {
      def toEntityList: EntityList = {
        fl.getFiles.asScala.map(GDrive.Entity.fromFile)
      }
    }
  }
}
