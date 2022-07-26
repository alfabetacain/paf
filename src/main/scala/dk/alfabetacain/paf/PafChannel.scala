package dk.alfabetacain.paf

import cats.implicits._
import java.nio.channels.SocketChannel
import cats.effect.kernel.Sync
import os.Path
import scala.reflect.ClassTag
import cats.effect.kernel.Resource
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

trait PafChannel[F[_]] {
  def writeMessage(message: Message): F[Unit]
  def readMessage[A <: Message: ClassTag](): F[A]
  def writeFileToChannel(from: os.Path, length: Long): F[Unit]
  def readFileFromChannel(to: os.Path, length: Long): F[Unit]
}

object PafChannel {

  def fromSocketChannel[F[_]: Sync](socket: SocketChannel): PafChannel[F] = {
    new PafChannel[F] {
      override def writeMessage(message: Message): F[Unit] = {
        Util.writeMessage[F](socket, message)
      }

      override def readMessage[A <: Message: ClassTag](): F[A] = {
        Util.readXMessage[F, A](socket.socket().getInputStream())
      }

      override def writeFileToChannel(from: Path, length: Long): F[Unit] = {
        Resource.make(Sync[F].interruptible(FileChannel.open(from.toNIO, StandardOpenOption.READ)))(f =>
          Sync[F].interruptible(f.close())
        ).use {
          in =>
            def doStuff(count: Long): F[Unit] = {
              val maxToRead = Math.min(Util.maxTransferSize, length - count)
              for {
                bytesRead <- Sync[F].interruptible(in.transferTo(count, maxToRead, socket))
                newCount = count + bytesRead
                _ <-
                  if (newCount < length) {
                    doStuff(newCount)
                  } else {
                    Sync[F].unit
                  }
              } yield ()
            }
            for {
              _ <- doStuff(0)
            } yield ()
        }
      }

      override def readFileFromChannel(to: Path, length: Long): F[Unit] = {
        val jfile = to.toIO
        val parent = jfile.getParentFile()
        val fileResource =
          for {
            parentExists <- Resource.eval(Sync[F].interruptible(parent.exists()))
            _ <-
              if (parentExists) {
                Resource.unit[F]
              } else {
                Resource.eval(Sync[F].interruptible(parent.mkdirs()))
              }
            fileChannel <- Resource.make(Sync[F].interruptible(FileChannel.open(
              to.toNIO,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING
            )))(s => Sync[F].interruptible(s.close()))
          } yield fileChannel

        fileResource.use { out =>
          def doStuff(count: Long): F[Unit] = {
            for {
              bytesRead <-
                Sync[F].interruptible(out.transferFrom(socket, count, Math.min(length - count, Util.maxTransferSize)))
              newCount = count + bytesRead
              _ <-
                if (newCount < length) {
                  doStuff(newCount)
                } else {
                  Sync[F].unit
                }
            } yield ()
          }
          for {
            _ <- doStuff(0)
          } yield ()
        }
      }
    }
  }
}
