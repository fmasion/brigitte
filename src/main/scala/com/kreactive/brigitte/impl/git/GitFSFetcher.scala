package com.kreactive.brigitte.impl.git

import akka.actor.{ActorSystem, Cancellable}
import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.Source
import better.files._
import com.kreactive.brigitte.State
import com.kreactive.brigitte.impl.FileSystemFetcher
import com.kreactive.brigitte.impl.git.GitState._
import configs.syntax._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{ObjectId, Ref}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * A [[com.kreactive.brigitte.Fetcher]] based on git commits. It's based on [[com.kreactive.brigitte.impl.FileSystemFetcher]].
  * Its source will emit only when there is a change in the head commit for the given branch
  *
  * @param branch       the branch to watch
  * @param remote       the conf for remote connection (if any)
  * @param delay        the delay between two checks on git
  * @param forcedFolder a folder where we want the repo to be (better leave as None)
  * @param tempFolder   the default folder where the repo should be
  * @param initFolder   docker hack
  */
class GitFSFetcher[T](
                       branch: String,
                       remote: Option[GitRemote],
                       delay: FiniteDuration,
                       forcedFolder: Option[File] = None,
                       tempFolder: File,
                       initFolder: Option[File],
                       fetcher: File => Future[T]
                     )(implicit executor: ExecutionContext, log: LoggingAdapter) extends FileSystemFetcher[T](delay, forcedFolder.getOrElse(tempFolder), initFolder, fetcher, keepDuplicate = false) {
  remote.foreach(_.init())

  /**
    * A source to know when to get the new conf. Can be overriden, e.g. using webhooks
    */
  def watcher: Source[Unit, Any] = Source.
    tick(Duration.Zero, delay, ())

  /**
    * fetch the new conf whenever the [[watcher]] wakes up
    *
    * @return
    */
  override def source: Source[T, Any] =
    watcher.
      via(State.scan((Option.empty[ObjectId], git))(update(remote) map (o => (_: Any) => List(o)))).
      mapAsync(1)(x => x).
      mapConcat(_.toList).
      via(distinctBy(identity))


  /**
    * ensures a git repo with a branch named `branch` tracking remote `origin/branch`.
    * If the repo does not exists locally yet, the folder will be deleted and the cloned repo will be bare (impossible to do changes locally)
    */
  val git: Git = cleanBranch(branch, remote) applyState Try(Git.open(folder.toJava)).recover { case _ if remote.isDefined => create(branch, remote.get, folder) }.get

  /**
    * get new conf and new last commit on local config
    */
  def localUpdate(prevCommit: Option[ObjectId]): State[Git, (Option[ObjectId], Future[Option[T]])] = lastOf(branch) >>= { headCommit =>
    if (prevCommit.contains(headCommit)) unit((prevCommit, Future.successful(None)))
    else safeFetch
  }

  /**
    * get new conf and new last commit on remote config
    */
  def remoteUpdate(prevCommit: Option[ObjectId])(implicit remote: GitRemote): State[Git, (Option[ObjectId], Future[Option[T]])] =
    remoteHead(branch).map(_.getObjectId) >>= { objectId =>
      if (prevCommit.contains(objectId)) unit(prevCommit, Future.successful(None))
      else safeFetch
    }

  /**
    * get new conf with stateful last commit and git
    */
  def update(remote: Option[GitRemote]): State[(Option[ObjectId], Git), Future[Option[T]]] =
    State.lift { prevCommit =>
      remote.fold(localUpdate(prevCommit)) { implicit r =>
        remoteUpdate(prevCommit)
      }
    }

  /**
    * checkout or force pull, depending on whether we are local or remote
    */
  private def checkoutBranch(implicit remote: GitRemote): GitState[Ref] =
    if (remote ne null) forcePull(branch) else checkout(branch)

  /**
    * stash, go to head of branch, get config, go back to original branch, unstash
    */
  private def safeFetch(implicit remote: GitRemote = null): State[Git, (Option[ObjectId], Future[Option[T]])] =
    branchName >>= { currentBranch =>
      stash >>= { wasStashed =>
        checkoutBranch map (ref => (Option(ref.getObjectId), fetcher(folder).map(Some(_)).recover {
          case e => log.error(e, "unable to parse conf")
            None
        })) >>= { tOpt =>
          checkout(currentBranch) >> safeUnstash(wasStashed) map (_ => tOpt)
        }
      }
    }
}