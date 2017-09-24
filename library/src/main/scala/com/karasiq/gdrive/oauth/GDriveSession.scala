package com.karasiq.gdrive.oauth

import com.google.api.client.auth.oauth2.Credential

@SerialVersionUID(0L)
final case class GDriveSession(userId: String, credential: Credential)
