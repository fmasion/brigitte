package com.kreactive.brigitte

import akka.stream.scaladsl.Source

trait Fetcher[T] {
  def source: Source[T, _]
}
