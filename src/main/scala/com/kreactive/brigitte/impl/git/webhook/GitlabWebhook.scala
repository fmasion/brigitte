package com.kreactive.brigitte.impl.git.webhook

import play.api.libs.json.Json

case class GitlabWebhook(ref: String)

object GitlabWebhook {
  implicit val format = Json.format[GitlabWebhook]
}
