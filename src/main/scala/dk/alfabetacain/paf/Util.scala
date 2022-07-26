package dk.alfabetacain.paf

import cats.effect.kernel.Sync
import java.io.InputStream
import cats.implicits._
import java.io.OutputStream
import scodec.bits.BitVector
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import java.nio.channels.WritableByteChannel
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import scribe.data.MDC
import java.io.IOException

object Util {

  val maxTransferSize: Long = 64 * 1024 * 1024

  def writeMessage[F[_]: Sync](out: WritableByteChannel, message: Message): F[Unit] = {
    val encoded = Format.message.encode(message).toOption.get.toByteArray
    val size    = scodec.codecs.int32.encode(encoded.length).toOption.get.toByteArray
    for {
      _ <- scribe.cats[F].info(s"Writing ${encoded.size} bytes for message $message")
      _ <- Sync[F].interruptible(out.write(ByteBuffer.wrap(size)))
      _ <- Sync[F].interruptible(out.write(ByteBuffer.wrap(encoded)))
    } yield ()
  }

  def readMessage[F[_]: Sync](in: InputStream): F[Message] = {
    for {
      size <- Sync[F].interruptible(in.readNBytes(4))
        .ensure(new IOException(s"Stream closed"))(_.length == 4)
        .flatMap(bytes =>
          scodec.codecs.int32.decode(BitVector(bytes)).toTry match {
            case Failure(exception) => Sync[F].raiseError[Int](exception)
            case Success(value)     => Sync[F].pure(value.value)
          }
        )
      _ <- scribe.cats[F].info(s"Reading message of size $size")
      message <- Sync[F].interruptible(in.readNBytes(size)).ensureOr(bytes =>
        new IllegalArgumentException(s"Expected to read $size bytes, but ${bytes.size} was read")
      )(_.size == size).flatMap(bytes =>
        Format.message.decode(BitVector(bytes)).toTry match {
          case Failure(exception) => Sync[F].raiseError[Message](exception)
          case Success(value)     => Sync[F].pure(value.value)
        }
      )
    } yield message
  }

  def readXMessage[F[_]: Sync, A <: Message: ClassTag](in: InputStream): F[A] = {
    readMessage[F](in).flatMap {
      case x: A => Sync[F].pure(x)
      case other =>
        Sync[F].raiseError(
          new IllegalArgumentException(s"Expected type ${implicitly[ClassTag[A]].runtimeClass}, but got $other")
        )
    }
  }

  def withMDC[F[_]: Sync, A](socket: SocketChannel)(action: MDC => F[A]): F[A] = {
    MDC { mdc =>
      for {
        remote <- Sync[F].delay(socket.getRemoteAddress().toString())
        _      <- Sync[F].delay(mdc("remote") = remote)
        res    <- action(mdc)
      } yield res
    }
  }
}
