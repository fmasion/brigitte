package com.kreactive.brigitte.impl.git
package webhook

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import better.files._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * A subclass of [[GitFSFetcher]] that allows push as well as push to check for new commits.
  *
  * @param port         the port on which Gitlab will hook itself.
  *                     This must be different from the port actually used in the config
  * @param path         the path on which Gitlab will hook itself.
  * @param branch       the branch to watch
  * @param remote       the conf for remote connection (if any)
  * @param forcedFolder a folder where we want the repo to be (better leave as None)
  * @param tempFolder   the default folder where the repo should be
  * @param pullDelay    the delay between checks, in case the webhook didn't work (optional)
  * @param initFolder   docker hack
  */
class HookedGitFSFetcher[T](
                             port: Int,
                             path: String,
                             branch: String,
                             remote: GitRemote,
                             forcedFolder: Option[File] = None,
                             tempFolder: File,
                             pullDelay: Option[FiniteDuration] = None,
                             initFolder: Option[File],
                             fetcher: File => Future[T]
                           )(implicit log: LoggingAdapter, executor: ExecutionContext, system: ActorSystem) extends GitFSFetcher[T](branch, Some(remote), pullDelay.getOrElse(Duration.Zero), forcedFolder, tempFolder, initFolder, fetcher) {

  def pushWatcher: Source[Unit, NotUsed] = Source.single(()) ++ Source.queue[Unit](1, OverflowStrategy.dropBuffer).
    mapMaterializedValue { queue =>
      GitlabDirective.catchHooks(branch, port, path, queue).
        andThen {
          case Success(_) => log.info(s"listening for push webhook on :$port/$path")
          case Failure(e) => log.error(e, "error while binding push port")
        }
    }

  override val watcher = pullDelay.fold(pushWatcher)(pushWatcher.keepAlive(_, () => ()))
}