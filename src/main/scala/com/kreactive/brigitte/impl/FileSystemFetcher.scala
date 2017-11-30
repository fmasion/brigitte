package com.kreactive.brigitte.impl

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.{Flow, Source}
import better.files._
import com.kreactive.brigitte.Fetcher

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implementation of [[com.kreactive.brigitte.Fetcher]] for file system input.
  * The output element are extracted using <fetcher> on the main folder.
  * Everything is checked for changes with a repeated scheduler
  *
  * @param delay      how often the files are checked for changes
  * @param folder     the root folder
  * @param initFolder a docker hack (I don't know what it really means @cyrille.corpet)
  * @param fetcher    how to extract interesting (and immutable) data from the folder
  */
class FileSystemFetcher[T](delay: FiniteDuration,
                           val folder: File,
                           initFolder: Option[File],
                           val fetcher: File => Future[T],
                           //TODO implement possibility to keep duplicates
                           keepDuplicate: Boolean = false
                          )(implicit
                            log: LoggingAdapter,
                            executor: ExecutionContext) extends Fetcher[T] {


  final def distinctBy[T, U](f: T => U): Flow[T, T, NotUsed] = Flow[T].scan((Option.empty[U], Option.empty[T])) {
    case ((None, _), t) => (Some(f(t)), Some(t))
    case ((Some(u), _), t) =>
      val uu = f(t)
      (Some(uu), Some(t).filterNot(_ => u == uu))
  }.mapConcat(_._2.toList)

  def source: Source[T, Any] =
    Source.tick(Duration.Zero, delay, ()).mapAsync(4)(_ => fetcher(folder)).via(distinctBy(identity))

  def init(env: String) = {
    initFolder.filter(_ => "docker".equalsIgnoreCase(env) && (!folder.exists || folder.isEmpty)) foreach { folderInit =>
      log.info("isEmpty " + folderInit.isEmpty)
      log.info("folderInit " + folderInit)
      log.info("folder " + folder)
      Dsl.cp(folderInit, folder)
    }
  }

}