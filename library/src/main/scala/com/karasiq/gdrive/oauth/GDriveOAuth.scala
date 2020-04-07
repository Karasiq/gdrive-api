package com.karasiq.gdrive.oauth

import java.io.StringReader

import com.google.api.client.extensions.java6.auth.oauth2.{AuthorizationCodeInstalledApp, VerificationCodeReceiver}
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.services.drive.DriveScopes
import com.typesafe.config.Config
import scala.collection.JavaConverters._

import com.karasiq.common.configs.ConfigEncoding
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.gdrive.context.GDriveContext

object GDriveOAuth {
  def apply(scopes: Set[String] = Set(DriveScopes.DRIVE))(implicit context: GDriveContext): GDriveOAuth = {
    new GDriveOAuth(scopes)
  }

  private def readSecrets(config: Config)(implicit context: GDriveContext): GoogleClientSecrets = {
    val configJson = ConfigEncoding.toJson(config)
    GoogleClientSecrets.load(context.jsonFactory, new StringReader(configJson))
  }
}

class GDriveOAuth(scopes: Set[String])(implicit context: GDriveContext) {
  object settings {
    val config = context.config.getConfigIfExists("oauth")
    val secrets = config.getConfigIfExists("secrets")
    val accessType = config.withDefault("offline", _.getString("access-type"))
  }

  object internal {
    lazy val secrets = createSecrets()
    lazy val authFlow = createAuthFlow(secrets)
  }

  object receiver {
    lazy val codeReceiver = createCodeReceiver()
    lazy val installedApp = new AuthorizationCodeInstalledApp(internal.authFlow, codeReceiver)
  }

  def authorize(userId: String): GDriveSession = {
    val credential = concurrent.blocking(receiver.installedApp.authorize(userId))
    GDriveSession(userId, credential)
  }

  protected def createSecrets(): GoogleClientSecrets = {
    GDriveOAuth.readSecrets(settings.secrets)
  }

  protected def createAuthFlow(secrets: GoogleClientSecrets): GoogleAuthorizationCodeFlow = {
    new GoogleAuthorizationCodeFlow.Builder(context.transport, context.jsonFactory, secrets, scopes.asJava)
      .setDataStoreFactory(context.dataStore)
      .setAccessType(settings.accessType)
      .build()
  }

  protected def createCodeReceiver(): VerificationCodeReceiver = {
    new LocalServerReceiver.Builder()
      // .setPort(19907)
      .build()
  }
}
