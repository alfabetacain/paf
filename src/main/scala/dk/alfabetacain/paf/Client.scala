package dk.alfabetacain.paf

import cats.implicits._
import cats.effect.kernel.Sync
import cats.effect.kernel.Resource
import java.net.Socket
import java.io.InputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.nio.channels.SocketChannel
import java.net.SocketAddress
import java.net.InetSocketAddress
import java.io.IOException

object Client {

  def run[F[_]: Sync](args: ClientArgs): F[Unit] = {

    val log      = scribe.cats[F]
    val settings = Message.Settings()
    val socketResource =
      Resource.make(Sync[F].interruptible(SocketChannel.open(new InetSocketAddress(args.host, args.port)))) { s =>
        Util.withMDC(s) { implicit mdc =>
          for {
            _ <- log.info("Terminating client connection...")
            _ <- Sync[F].interruptible(s.shutdownOutput()).void.recover { case _: IOException => () }
            _ <- Sync[F].interruptible(s.shutdownInput()).void.recover { case _: IOException => () }
            _ <- Sync[F].interruptible(s.close()).void.recover { case _: IOException => () }
            _ <- log.info("Client connection terminated")
          } yield ()
        }
      }

    socketResource.use { socket =>
      Util.withMDC(socket) { implicit mdc =>
        val channel = PafChannel.fromSocketChannel[F](socket)
        for {
          _              <- log.info("Writing settings...")
          _              <- channel.writeMessage(settings)
          _              <- log.info("Reading settings...")
          serverSettings <- channel.readMessage[Message.Settings]()
          sessionSettings = Message.Settings.unify(settings, serverSettings)
          _ <- log.info(s"Unified settings = $sessionSettings")

          _        <- log.info("Reading manifest...")
          manifest <- channel.readMessage[Message.Manifest]()

          _ <- manifest.files.map { file =>
            val path = args.outputFolder / file.path
            for {
              _ <- log.info(s"Downloading file $file")
              _ <- channel.readFileFromChannel(path, file.size)
            } yield ()

          }.sequence
        } yield ()
      }
    }
  }
}
