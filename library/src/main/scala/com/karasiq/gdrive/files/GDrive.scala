package com.karasiq.gdrive.files

import scala.collection.JavaConverters._
import scala.language.implicitConversions

import com.google.api.services.drive.{model ⇒ gd}

object GDrive {
  final case class Entity(id: String, name: String, parents: Seq[String])

  object Entity {
    private[gdrive] val fields = "id, name, parents"
    private[gdrive] val listFields = s"files($fields)"

    implicit def fromFile(file: gd.File): Entity = {
      Entity(file.getId, file.getName, file.getParents.asScala.toVector)
    }
  }
}
