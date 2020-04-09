package com.karasiq.gdrive.files

import java.io.{InputStream, OutputStream}

import com.google.api.client.util.{IOUtils, Sleeper}
import com.google.api.services.drive.model.{File, FileList, TeamDrive}
import com.google.api.services.drive.{Drive, DriveRequest}
import com.karasiq.gdrive.context.GDriveContext
import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.gdrive.oauth.GDriveSession
import com.karasiq.gdrive.query.GDriveUtils

import scala.collection.JavaConverters._

object GDriveService {

  final case class TeamDriveId(id: String) extends AnyVal {
    def enabled: Boolean = id != null

    def root = if (enabled) GDrive.Entity(id, "", Nil, 0) else GDriveUtils.RootEntity
  }

  object TeamDriveId {
    val none = TeamDriveId(null)
  }

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

  import com.karasiq.gdrive.query.GApiQuery.DSL._
  import com.karasiq.gdrive.query.{GDriveQueries => Q}
  import implicits._

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
  def allFolders()(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    fileQuery(Q.isFolder && Q.nonTrashed)
  }

  def foldersIn(parentId: EntityId)(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    fileQuery(Q.isFolder && Q.nonTrashed && Q.parent(parentId))
  }

  def folder(folderId: EntityId): GDrive.Entity = {
    this.file(folderId)
  }

  def folderAt(path: EntityPath)(implicit td: TeamDriveId = TeamDriveId.none): GDrive.Entity = {
    path.foldLeft(td.root) { (parent, name) ⇒
      folderInWithName(parent.id, name)(td).getOrElse(throw new NoSuchElementException(name))
    }
  }

  def createFolder(path: EntityPath)(implicit td: TeamDriveId = TeamDriveId.none): GDrive.Entity = {
    path.foldLeft(td.root) { (parent, name) ⇒
      folderInWithName(parent.id, name).getOrElse {
        createFolderWithName(parent.id, name)
        folderInWithName(parent.id, name).get // Re-query
      }
    }
  }

  def folderInWithName(parentId: EntityId, name: String)(implicit td: TeamDriveId = TeamDriveId.none) = {
    fileQuery(Q.isFolder && Q.nonTrashed && Q.parent(parentId) && Q.name(name))
      .toStream
      .headOption
  }

  def createFolderWithName(parentId: EntityId, name: String)(implicit td: TeamDriveId = TeamDriveId.none): GDrive.Entity = {
    val file = new File()
      .setName(name)
      .setMimeType(GDriveUtils.FolderMime)
      .setParents(Seq(parentId).asJava)
      .setTeamDriveId(td.id)

    driveService.files().create(file).setSupportsTeamDrives(td.enabled).toEntity
  }

  def traverseFolder(path: EntityPath)(implicit td: TeamDriveId = TeamDriveId.none): Iterator[(EntityPath, GDrive.Entity)] = {
    def traverseFolderRec(path: EntityPath, folderId: EntityId): Iterator[(EntityPath, GDrive.Entity)] = {
      def files() = this.filesIn(folderId)(td)

      def subFolders() = this.foldersIn(folderId)(td)

      files().map((path, _)) ++ subFolders().flatMap(folder ⇒ traverseFolderRec(path :+ folder.name, folder.id))
    }

    traverseFolderRec(path, this.folderAt(path).id)
  }

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def allFiles()(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    fileQuery(null)
  }

  def fileQuery(query: String)(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    driveService.files().list()
      .setCorpora(if (td.enabled) "teamDrive" else "user")
      .setTeamDriveId(td.id)
      .setIncludeTeamDriveItems(td.enabled)
      .setSupportsTeamDrives(td.enabled)
      .setOrderBy("createdTime")
      .setQ(query)
      .toEntityStream
  }

  def filesIn(parentId: EntityId)(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    fileQuery(Q.isFile && Q.nonTrashed && Q.parent(parentId))
  }

  def filesInWithName(parentId: EntityId, name: String)(implicit td: TeamDriveId = TeamDriveId.none): EntityStream = {
    fileQuery(Q.isFile && Q.nonTrashed && Q.parent(parentId) && Q.name(name))
  }

  def file(id: EntityId): GDrive.Entity = {
    driveService.files().get(id).setSupportsTeamDrives(true).toEntity
  }

  def fileExists(parentId: EntityId, name: String)(implicit td: TeamDriveId = TeamDriveId.none): Boolean = {
    filesInWithName(parentId, name).nonEmpty
  }

  def delete(fileId: EntityId): Unit = {
    driveService.files()
      .delete(fileId)
      .setSupportsTeamDrives(true)
      .execute()
  }

  def update(fileId: EntityId, attrs: File => File): Unit = {
    driveService.files()
      .update(fileId, attrs(new File))
      .execute()
  }

  // -----------------------------------------------------------------------
  // Upload/download
  // -----------------------------------------------------------------------
  def upload(parentId: EntityId, name: String, content: GDriveContent, safe: Boolean = false)(implicit td: TeamDriveId = TeamDriveId.none): GDrive.Entity = {
    val fileMetadata = {
      val base = new File().setTeamDriveId(td.id)
      if (safe) base.setName(s"$name.tmp")
      else base.setName(name).setParents(Seq(parentId).asJava)
    }

    val request = driveService.files()
      .create(fileMetadata, content)
      .setSupportsTeamDrives(true)
      .setDisableGZipContent(true)

    //noinspection ConvertExpressionToSAM
    request.getMediaHttpUploader
      .setSleeper(new Sleeper {
        def sleep(millis: Long): Unit = {
          // System.err.println(s"[${Thread.currentThread()}] Sleeping $millis ms")
          concurrent.blocking(Thread.sleep(millis))
        }
      })
      .setDirectUploadEnabled(true)
      .setDisableGZipContent(true)

    if (safe) {
      val fileId = concurrent.blocking(request.toEntityId)
      driveService.files()
        .update(fileId, new File().setName(name))
        .setAddParents(parentId)
        .setSupportsTeamDrives(true)
        .toEntity
    } else request.toEntity
  }

  def download(fileId: EntityId): InputStream = {
    val request = driveService.files().get(fileId)

    request.setSupportsTeamDrives(true)
    request.setDisableGZipContent(true)
    request.getMediaHttpDownloader.setDirectDownloadEnabled(true)

    request.executeMediaAsInputStream()
  }

  def download(fileId: EntityId, outputStream: OutputStream): Unit = {
    val inputStream = download(fileId)
    concurrent.blocking(IOUtils.copy(inputStream, outputStream))
  }

  // -----------------------------------------------------------------------
  // Team drives
  // -----------------------------------------------------------------------
  def teamDrives(): Seq[TeamDrive] = {
    driveService.teamdrives().list().execute().getTeamDrives.asScala
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

      def toEntityId: EntityId = {
        request.setFields("id").execute().getId
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
      def toEntityList: EntityList =
        fl.getFiles.asScala.map(GDrive.Entity.fromFile)
    }
  }
}
