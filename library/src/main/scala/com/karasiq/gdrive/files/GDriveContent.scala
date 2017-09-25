package com.karasiq.gdrive.files

import java.io.{File, InputStream}
import java.nio.charset.StandardCharsets

import scala.language.implicitConversions

import com.google.api.client.http.{AbstractInputStreamContent, ByteArrayContent, FileContent, InputStreamContent}

import com.karasiq.gdrive.query.GDriveUtils

final case class GDriveContent(content: AbstractInputStreamContent) extends AnyVal

object GDriveContent {
  implicit def fromInputStream(inputStream: InputStream) = {
    GDriveContent(new InputStreamContent(GDriveUtils.DefaultMime, inputStream))
  }

  implicit def fromBytes(bytes: Array[Byte]) = {
    GDriveContent(new ByteArrayContent(GDriveUtils.DefaultMime, bytes))
  }

  implicit def fromString(str: String) = {
    fromBytes(str.getBytes(StandardCharsets.UTF_8))
  }

  implicit def fromFile(file: File) = {
    GDriveContent(new FileContent(GDriveUtils.DefaultMime, file))
  }

  implicit def toInputStreamContent(c: GDriveContent): AbstractInputStreamContent = c.content
}