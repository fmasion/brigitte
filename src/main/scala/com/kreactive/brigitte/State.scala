package com.kreactive.brigitte

import akka.stream.scaladsl.Flow
import shapeless.{lens, Lens}

import scala.collection.immutable

/**
  * This is the well loved state monad.
  * A `State[S, T]` is just a `S => (S, T)` so it's a description on how to get a `T` from an `S`,
  * possibly changing the `S` in the process
  * @tparam S the state type, invariant
  * @tparam T the output type
  */
trait State[S, +T] extends (S => (S, T)){
  /**
    * applies `f` on the value, without changing the state
    */
  def map[U](f: T => U) = State{(g: S) =>
    val (gg, t) = apply(g)
    (gg, f(t))
  }

  /**
    * Sequence two stateful operations, using the result of the first one.
    */
  def flatMap[U](f: T => State[S, U]) = State{ (g: S) =>
    val (gg, t) = apply(g)
    f(t).apply(gg)
  }

  /**
    * shorthand for [[flatMap]]
    */
  def >>=[U](f: T => State[S, U]): State[S, U] = flatMap(f)

  /**
    * Sequence two stateful operations, ignoring the output of the first one.
    */
  def >>[U](f: State[S, U]): State[S, U] = flatMap(_ => f)

  /**
    * get state ofter the stateful operation has been run on the given initial state
    */
  def applyState(init: S) = apply(init)._1

  /**
    * consider this as a stateful operation on a larger state,
    * while not modifying anything on this larger state, except this particular part
    */
  def lift[A](l: Lens[A, S]): State[A, T] = State{ a: A =>
    val (s, t) = apply(l.get(a))
    (l.set(a)(s), t)
  }

  /**
    * [[lift]] in a tuple, on the left element
    */
  def liftLeft[SS]: State[(S, SS), T] = lift(lens[(S, SS)]._1)
  /**
    * [[lift]] in a tuple, on the right element
    */
  def liftRight[SS]: State[(SS, S), T] = lift(lens[(SS, S)]._2)

  /**
    * flattens heterogeneously the given `State[S, State[A, B]]`
    */
  def hFlatten[A, B](implicit ev: T <:< State[A, B]): State[(S, A), B] =
    liftLeft[A] flatMap (_.liftRight)
}

object State {
  /**
    * unit of state monad. Does nothing on state, returns value
    */
  def unit[S, T](t: T): State[S, T] = apply((s: S) => (s, t))

  /**
    * shorthand for defining custom `State`
    */
  def apply[S, T](f: S => (S, T)): State[S, T] = new State[S, T] {
    override def apply(v1: S): (S, T) = f(v1)
  }

  /**
    * a simple `State` that replaces the state by the given value
    */
  def set[S](s: S): State[S, Unit] =
    apply((_: S) => (s, ()))

  /**
    * a simple `State` that outputs the state as value
    */
  def get[S]: State[S, S] =
    apply((s: S) => (s, s))

  /**
    * An implementation of `akka.stream.scaladsl.Flow.scan` using the `State` monad
    */
  def scan[In, S, Out](init: S)(f: State[S, In => immutable.Seq[Out]]) = Flow[In].scan((init, immutable.Seq.empty[Out])){
    case ((s, _), in) =>
      val (ss, ap) = f(s)
      (ss, ap(in))
  }.mapConcat(_._2.toList)

  /**
    * lifts the given function to a stateful computation on the parameter and the output state
    */
  def lift[S1, S2, T](f: S1 => State[S2, (S1, T)]): State[(S1, S2), T] = for {
    oldS1 <- State.get[S1].liftLeft
    p <- f(oldS1).liftRight
    (s1, t) = p
    _ <- State.set(s1).liftLeft
  } yield t


}

