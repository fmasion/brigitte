package com.kreactive.brigitte.impl.git.webhook

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive0, Route}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.SourceQueueWithComplete
import com.kreactive.http.PlayJsonSupport

import scala.concurrent.Future

trait GitlabDirective extends PlayJsonSupport {
  def hookPath(p: String) = path(separateOnSlashes(p))
  def pushHook: Directive0 = extractRequest map { req =>
    req.headers.find(_.is("X-Gitlab-Event".toLowerCase)).map(_.value())
  } require (_.contains("Push Hook"))

  def onlyOn(branch: String) = entity(as[GitlabWebhook]) require (_.ref == s"refs/heads/$branch")

  def webhookRoute(branch: String, path: String, queue: SourceQueueWithComplete[Unit]): Route = {
    post {
      hookPath(path) {
        pushHook {
          onlyOn(branch) {
            queue.offer(())
            complete(StatusCodes.OK)
          } ~ complete(StatusCodes.Accepted)
        }
      }
    }
  }
  def catchHooks(branch: String, port: Int, path: String, queue: SourceQueueWithComplete[Unit])(implicit system: ActorSystem): Future[Http.ServerBinding] = {
    implicit val mat = ActorMaterializer()
    Http().bindAndHandle(Route.handlerFlow(webhookRoute(branch, path, queue)), "0.0.0.0", port)
  }
}

object GitlabDirective extends GitlabDirective
