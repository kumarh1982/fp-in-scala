import fpinscala.parallelism.Par
import fpinscala.laziness.Stream
import fpinscala.parallelism.Par.Par
import fpinscala.parsing.Parsers
import fpinscala.testing.Gen

import scala.language.higherKinds

trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]

  def distribute[A, B](fab: F[(A, B)]): (F[A], F[B]) =
    (map(fab)(_._1), map(fab)(_._2))

  def codistribute[A, B](e: Either[F[A], F[B]]): F[Either[A, B]] = e match {
    case Left(fa) => map(fa)(Left(_))
    case Right(fb) => map(fb)(Right(_))
  }
}

trait Monad[F[_]] extends Functor[F] {
  def unit[A](a: => A): F[A]

  def flatMap[A, B](ma: F[A])(f: A => F[B]): F[B]

  def map[A, B](ma: F[A])(f: A => B): F[B] =
    flatMap(ma)(a => unit(f(a)))

  def map2[A, B, C](ma: F[A], mb: F[B])(f: (A, B) => C): F[C] =
    flatMap(ma)(a => map(mb)(b => f(a, b)))

  def sequence[A](lma: List[F[A]]): F[List[A]] =
    lma.foldRight(unit(List[A]()))((fa, fla) => map2(fa, fla)(_ :: _))

  def traverse[A, B](la: List[A])(f: A => F[B]): F[List[B]] =
    la.foldRight(unit(List[B]()))((a, flb) => map2(f(a), flb)(_ :: _))

  def replicateM[A](n: Int, ma: F[A]): F[List[A]] = {
    if (n == 0) unit(List[A]())
    else map2(ma, replicateM(n - 1, ma))((a, b) => a :: b)
  }

  def compose[A, B, C](f: A => F[B], g: B => F[C]): A => F[C] = {
    a => flatMap(f(a))(g)
  }

  //reference solution
  def flatMapViaCompose[A, B](ma: F[A])(f: A => F[B]): F[B] = {
    compose((_: Unit) => ma, f)(())
  }

  def join[A](mma: F[F[A]]): F[A] = {
    flatMap(mma)(ma => ma)
  }

  def flatMapViaJoin[A, B](ma: F[A])(f: A => F[B]): F[B] = {
    join(map(ma)(f))
  }

}

case class Id[A](value: A) {
  def flatMap[B](f: (A) => Id[B]): Id[B] = {
    f(value)
  }

  def map[B](f: (A) => B): Id[B] = {
    Id(f(value))
  }
}

case class Reader[R, A](run: R => A)
object Reader {
  def ask[R]: Reader[R, R] = Reader(r => r)
}

object Monad {
  val genMonad = new Monad[Gen] {
    def unit[A](a: => A): Gen[A] = Gen.unit(a)
    override def flatMap[A, B](ma: Gen[A])(f: A => Gen[B]): Gen[B] =
      ma flatMap f
  }

  val parMonad = new Monad[Par] {
    override def unit[A](a: => A): Par[A] = Par.lazyUnit(a)

    override def flatMap[A, B](ma: Par[A])(f: (A) => Par[B]): Par[B] =
      Par.flatMap(ma)(f)
  }

  def parserMonad[ParserError, P[+_]](p: Parsers[ParserError, P]) = new Monad[P] {
    override def unit[A](a: => A): P[A] = p.succeed(a)

    override def flatMap[A, B](ma: P[A])(f: (A) => P[B]): P[B] =
      p.flatMap(ma)(f)
  }

  def optionMonad = new Monad[Option] {
    override def unit[A](a: => A): Option[A] = Some(a)

    override def flatMap[A, B](ma: Option[A])(f: (A) => Option[B]): Option[B] =
      ma.flatMap(f)
  }

  def streamMonad = new Monad[Stream] {
    override def unit[A](a: => A): Stream[A] = Stream(a)

    override def flatMap[A, B](ma: Stream[A])(f: (A) => Stream[B]): Stream[B] =
      ma.flatMap(f)
  }

  def idMonad = new Monad[Id] {
    override def unit[A](a: => A): Id[A] = Id(a)

    override def flatMap[A, B](ma: Id[A])(f: (A) => Id[B]): Id[B] =
      ma.flatMap(f)
  }

  def readerMonad[R] = new Monad[({ type aReader[A] = Reader[R, A] })#aReader] {
    override def unit[A](a: => A): Reader[R, A] = Reader(r => a)

    override def flatMap[A, B](ma: Reader[R, A])(f: (A) => Reader[R, B]): Reader[R, B] =
      Reader(r => f(ma.run(r)).run(r))
  }

  def eitherMonad[E] = new Monad[({ type f[x] = Either[E, x] })#f] {
    override def unit[A](a: => A): Either[E, A] = Right(a)

    override def flatMap[A, B](ma: Either[E, A])(f: (A) => Either[E, B]): Either[E, B] =
      ma.fold(Left(_), f)

  }

}