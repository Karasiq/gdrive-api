package com.karasiq.gdrive.files

import scala.collection.JavaConverters._
import scala.language.implicitConversions

import com.google.api.services.drive.{model ⇒ gd}

object GDrive {
  final case class Entity(id: String, name: String, parents: Seq[String], size: Long)

  object Entity {
    private[gdrive] val fields = "id, name, parents, size"
    private[gdrive] val listFields = s"files($fields)"

    implicit def fromFile(file: gd.File): Entity = {
      Entity(file.getId, file.getName, file.getParents.asScala.toVector, file.getSize)
    }
  }

  final case class Quota(totalSize: Long, usedSize: Long, maxUploadSize: Long)

  object Quota {
    private[gdrive] val fields = "maxUploadSize, storageQuota(limit, usage)"
    
    implicit def fromAbout(about: gd.About): Quota = {
      Quota(about.getStorageQuota.getLimit, about.getStorageQuota.getUsage, about.getMaxUploadSize)
    }
  }
}
