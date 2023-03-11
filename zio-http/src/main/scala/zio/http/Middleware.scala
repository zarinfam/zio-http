package zio.http

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object Middleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
    type OutEnv[Env]
    type OutErr[Err]

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      http: Http[Env, Err, Request, Response],
    )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], Request, Response]
  }

  trait Simple[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] extends Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err
  }

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow: Allow = new Allow(())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO: AllowZIO = new AllowZIO(())

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity: Middleware[Nothing, Any, Nothing, Any] =
    new Middleware.Simple[Nothing, Any, Nothing, Any] {
      override def apply[Env, Err](
        http: Http[Env, Err, Request, Response],
      )(implicit trace: Trace): Http[Env, Err, Request, Response] =
        http
    }

  final class Allow(val unit: Unit) extends AnyVal {
    def apply(condition: Request => Boolean): Middleware[Nothing, Any, Nothing, Any] =
      new Middleware.Simple[Nothing, Any, Nothing, Any] {
        override def apply[Env, Err](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          http.when(condition)
      }
  }

  final class AllowZIO(val unit: Unit) extends AnyVal {
    def apply[R, Err](
      condition: Request => ZIO[R, Err, Boolean],
    ): Middleware[Nothing, R, Err, Any] =
      new Middleware.Simple[Nothing, R, Err, Any] {
        override def apply[Env <: R, Err1 >: Err](
          http: Http[Env, Err1, Request, Response],
        )(implicit trace: Trace): Http[Env, Err1, Request, Response] =
          http.whenZIO(condition)
      }
  }

  implicit final class MiddlewareSyntax[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr](
    val self: Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr],
  ) extends AnyVal {

    /**
     * Applies Middleware based only if the condition function evaluates to true
     */
    def when(condition: Request => Boolean)(implicit trace: Trace): Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr] =
      new Middleware.Simple[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          ???
      }

    /**
     * Applies Middleware based only if the condition effectful function
     * evaluates to true
     */
    def whenZIO[UpperEnv1 <: UpperEnv, LowerErr1 >: LowerErr](
      condition: Request => ZIO[UpperEnv1, LowerErr1, Boolean],
    )(implicit
      trace: Trace,
    ): Middleware[LowerEnv, UpperEnv1, LowerErr1, UpperErr] =
      new Middleware.Simple[LowerEnv, UpperEnv1, LowerErr1, UpperErr] {
        override def apply[Env >: LowerEnv <: UpperEnv1, Err >: LowerErr1 <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          ???
      }
  }
}
