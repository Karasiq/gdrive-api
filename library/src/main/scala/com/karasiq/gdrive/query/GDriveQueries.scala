package com.karasiq.gdrive.query

import com.karasiq.gdrive.query.GApiQuery.DSL._

object GDriveQueries {
  private[this] val F = GDriveFields

  val isFolder = F.mimeType === GDriveUtils.FolderMime
  val isFile = F.mimeType !== GDriveUtils.FolderMime
  val nonTrashed = F.trashed === false
  def name(name: String) = F.name === name
  def parent(parentId: String) = parentId.literal in F.parents
}
