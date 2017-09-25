package com.karasiq.gdrive.files

import java.io.{File, InputStream}
import java.nio.charset.StandardCharsets

import scala.language.implicitConversions

import com.google.api.client.http.{AbstractInputStreamContent, ByteArrayContent, FileContent, InputStreamContent}

final case class GDriveContent(content: AbstractInputStreamContent) extends AnyVal

object GDriveContent {
  val DefaultContentType = "application/octet-stream"

  implicit def fromInputStream(inputStream: InputStream) = {
    GDriveContent(new InputStreamContent(DefaultContentType, inputStream))
  }

  implicit def fromBytes(bytes: Array[Byte]) = {
    GDriveContent(new ByteArrayContent(DefaultContentType, bytes))
  }

  implicit def fromString(str: String) = {
    fromBytes(str.getBytes(StandardCharsets.UTF_8))
  }

  implicit def fromFile(file: File) = {
    GDriveContent(new FileContent(DefaultContentType, file))
  }

  implicit def toInputStreamContent(c: GDriveContent): AbstractInputStreamContent = c.content
}