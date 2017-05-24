package org.http4s
package argonaut

import _root_.argonaut.{DecodeResult => ArgDecodeResult, _}
import Argonaut._
import cats.{Applicative, MonadError}
import org.http4s.headers.`Content-Type`

trait ArgonautInstances {
  implicit def jsonDecoder[F[_]: MonadError[?[_], Throwable]]: EntityDecoder[F, Json] = {
    import Parser.facade
    jawn.jawnDecoder[F, Json]
  }

  def jsonOf[F[_]: MonadError[?[_], Throwable], A](implicit decoder: DecodeJson[A]): EntityDecoder[F, A] =
    jsonDecoder[F].flatMapR { json =>
      decoder.decodeJson(json)
        .fold(
          (message, history) => DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json, error: $message, cursor: $history")),
          DecodeResult.success(_)
        )
    }

  protected def defaultPrettyParams: PrettyParams

  implicit def jsonEncoder[F[_]: Applicative]: EntityEncoder[F, Json] =
    jsonEncoderWithPrettyParams[F](defaultPrettyParams)

  def jsonEncoderWithPrettyParams[F[_]](prettyParams: PrettyParams)
                                       (implicit A: Applicative[F]): EntityEncoder[F, Json] =
    EntityEncoder
      .stringEncoder(A, Charset.`UTF-8`)
      .contramap[Json](prettyParams.pretty)
      .withContentType(`Content-Type`(MediaType.`application/json`, Charset.`UTF-8`))

  def jsonEncoderOf[F[_]: Applicative, A](implicit encoder: EncodeJson[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrinterOf(defaultPrettyParams)

  def jsonEncoderWithPrinterOf[F[_]: Applicative, A](prettyParams: PrettyParams)
                                                    (implicit encoder: EncodeJson[A]): EntityEncoder[F, A] =
    jsonEncoderWithPrettyParams[F](prettyParams).contramap[A](encoder.encode)

  implicit val uriCodec: CodecJson[Uri] = CodecJson(
    (uri: Uri) => Json.jString(uri.toString),
    c => c.as[String].flatMap(str =>
      Uri.fromString(str).fold(err => ArgDecodeResult.fail(err.toString, c.history), ArgDecodeResult.ok))
  )
}

object ArgonautInstances {
  def withPrettyParams(pp: PrettyParams): ArgonautInstances =
    new ArgonautInstances {
      def defaultPrettyParams: PrettyParams = pp
    }
}
