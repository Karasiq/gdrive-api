package com.karasiq.gdrive.query

import com.karasiq.gdrive.files.GDrive

object GDriveUtils {
  val DefaultMime = "application/octet-stream"
  val FolderMime = "application/vnd.google-apps.folder"
  val RootEntity = GDrive.Entity("root", "", Nil, 0)
}
