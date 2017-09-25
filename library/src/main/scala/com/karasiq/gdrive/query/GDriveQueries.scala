package com.karasiq.gdrive.query

import com.karasiq.gdrive.query.GApiQuery.DSL._

object GDriveQueries {
  val isFolder = "mimeType" === "application/vnd.google-apps.folder"
  val isFile = "mimeType" !== "application/vnd.google-apps.folder"
  val nonTrashed = "trashed" === false
  def name(name: String) = "name" === name
  def parent(parentId: String) = parentId.literal in "parents".field
}
