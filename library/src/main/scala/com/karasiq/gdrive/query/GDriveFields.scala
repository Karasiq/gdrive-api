package com.karasiq.gdrive.query

import com.karasiq.gdrive.query.GApiQuery.DSL._
import com.karasiq.gdrive.query.GApiQuery.Expression.FieldName

object GDriveFields {
  val mimeType: FieldName = 'mimeType
  val trashed: FieldName = 'trashed
  val name: FieldName = 'name
  val parents: FieldName = 'parents
  val fullText: FieldName = 'fullText
  val modifiedTime: FieldName = 'modifiedTime
  val viewedByMeTime: FieldName = 'viewedByMeTime
  val starred: FieldName = 'starred
  val owners: FieldName = 'owners
  val writers: FieldName = 'writers
  val readers: FieldName = 'readers
  val sharedWithMe: FieldName = 'sharedWithMe
  val properties: FieldName = 'properties
  val appProperties: FieldName = 'appProperties
  val visibility: FieldName = 'visibility
}
