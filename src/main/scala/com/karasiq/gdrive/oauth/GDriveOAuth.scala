package com.karasiq.gdrive.oauth

import java.io.StringReader

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.services.drive.DriveScopes
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigEncoding
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.gdrive.context.GDriveContext

object GDriveOAuth {
  def apply()(implicit context: GDriveContext): GDriveOAuth = {
    new GDriveOAuth()
  }

  private def readSecrets(config: Config)(implicit context: GDriveContext): GoogleClientSecrets = {
    val configJson = ConfigEncoding.toJson(config)
    GoogleClientSecrets.load(context.jsonFactory, new StringReader(configJson))
  }
}

class GDriveOAuth(implicit context: GDriveContext) {
  protected object Settings {
    val config = context.config.getConfigIfExists("oauth")
    val secrets = config.getConfigIfExists("secrets")
    val accessType = config.withDefault("offline", _.getString("access-type"))
  }

  private[this] val secrets = GDriveOAuth.readSecrets(Settings.secrets)
  private[this] val authFlow = new GoogleAuthorizationCodeFlow.Builder(context.transport, context.jsonFactory, secrets, DriveScopes.all())
    .setDataStoreFactory(context.dataStore)
    .setAccessType(Settings.accessType)
    .build()

  private[this] val receiver: LocalServerReceiver = new LocalServerReceiver.Builder()
    // .setPort(19907)
    .build()
  
  private[this] val installedApp = new AuthorizationCodeInstalledApp(authFlow, receiver)

  def authorize(userId: String): GDriveSession = {
    val credential = concurrent.blocking(installedApp.authorize(userId))
    GDriveSession(userId, credential)
  }
}
