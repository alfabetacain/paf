package dk.alfabetacain.paf

import scala.concurrent.duration._
import cats.effect.implicits._
import cats.effect.kernel.Async
import cats.effect.kernel.GenSpawn
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.std.Supervisor
import cats.implicits._

import java.io.FileInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import scodec.codecs.int32
import cats.effect.kernel.Sync
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.nio.channels.ServerSocketChannel
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel
import scribe.data.MDC
import java.io.IOException

object Server {

  def run[F[_]: Async](args: ServerArgs): F[Unit] = {

    val log = scribe.cats[F]
    val serverResource = Resource.make {
      for {
        _      <- log.info("Waiting for server socket...")
        server <- Async[F].interruptible(ServerSocketChannel.open())
        _      <- Async[F].interruptible(server.bind(new InetSocketAddress(args.host, args.port)))
        _      <- log.info("Acquired server socket")
      } yield server
    } { s =>
      for {
        _ <- log.info("Closing server socket...")
        _ <- Async[F].interruptible(s.close()).void.recover { case _: IOException => () }
        _ <- log.info("Server socket closed")
      } yield ()
    }
    val resources = for {
      server        <- serverResource
      watchedFolder <- WatchedFolder.makeStatic[F](args.sourceFolder)
      supervisor    <- Supervisor[F].onFinalize(log.info("Supervisor closed"))
    } yield (server, watchedFolder, supervisor)
    resources.use { case (server, watchedFolder, supervisor) =>
      doRun(Message.Settings(), watchedFolder, server, supervisor)
    }
  }

  private def doRun[F[_]: Async](
      settings: Message.Settings,
      watchedFolder: WatchedFolder[F],
      server: ServerSocketChannel,
      supervisor: Supervisor[F]
  ): F[Unit] = {
    for {
      res <- connection(server).allocated
      (conn, finalizer) = res
      _ <- supervisor.supervise(handle(settings, watchedFolder, conn).guarantee(finalizer).recoverWith {
        case err: Throwable =>
          Util.withMDC(conn) { implicit mdc =>
            scribe.cats[F].error("Handling error", err)
          }
      })
      _ <- doRun(settings, watchedFolder, server, supervisor)
    } yield ()
  }

  private def connection[F[_]: Sync](server: ServerSocketChannel): Resource[F, SocketChannel] = {
    Resource.makeCase(Sync[F].interruptible(server.accept())) {
      case (s, _) =>
        val log = scribe.cats[F]
        for {
          _ <- log.info("Closing server connection...")
          _ <- Sync[F].interruptible(s.close()).void.recover { case _: IOException => () }
          _ <- log.info("Server connection closed")
        } yield ()
    }
  }

  private def handle[F[_]: Async](
      settings: Message.Settings,
      watchedFolder: WatchedFolder[F],
      socket: SocketChannel
  ): F[Unit] = {
    val log           = scribe.cats[F]
    val remoteAddress = socket.getRemoteAddress()
    Util.withMDC(socket) { implicit mdc =>
      val channel = PafChannel.fromSocketChannel[F](socket)
      for {
        _              <- log.info("New connection")
        _              <- log.info("Sending server settings...")
        _              <- channel.writeMessage(settings)
        _              <- log.info("Reading client settings...")
        clientSettings <- channel.readMessage[Message.Settings]()
        sessionSettings = Message.Settings.unify(settings, clientSettings)
        _ <- log.info(s"Session settings = $sessionSettings")

        files <- watchedFolder.getFiles()

        manifest = Message.Manifest(files.map(_._2))
        _ <- log.info(s"Sending manifest... $manifest")
        _ <- channel.writeMessage(manifest)

        _ <- files.map { x =>
          for {
            _ <- log.info(s"Sending file ${x._1}")
            _ <- channel.writeFileToChannel(x._1, x._2.size)
          } yield ()
        }.sequence

        // wait until done
        _ <- Sync[F].race(
          channel.readMessage[Message.Done]().recover { case _: IOException => Message.Done() },
          Sync[F].sleep(1.minute)
        )
        _ <- log.info(s"Closing connection to $remoteAddress...")
      } yield ()
    }
  }
}
