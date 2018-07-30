# gdrive-api
Scala Google Drive API
```scala
"com.github.karasiq" %% "gdrive-api" % "1.0.13"
```

# Config
GDrive context configuration should contain `oauth.secrets` section with the secrets JSON.

```
oauth.secrets = {"installed": {"client_id": "*******", "project_id": "*******", "auth_uri": "https://accounts.google.com/o/oauth2/auth", "token_uri": "https://accounts.google.com/o/oauth2/token", "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs", "client_secret": "*****************", "redirect_uris": ["urn:ietf:wg:oauth:2.0:oob", "http://localhost"]}}
```
