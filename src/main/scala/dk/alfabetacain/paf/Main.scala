package dk.alfabetacain.paf

import cats.effect.IOApp
import cats.effect.{ ExitCode, IO }
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import cats.effect.kernel.Resource
import java.net.ServerSocket
import collection.JavaConverters._
import java.io.InputStream
import java.nio.file.{ Path => JPath }
import scala.annotation.tailrec
import java.io.OutputStream
import java.io.FileOutputStream
import java.net.Socket
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.net.SocketAddress
import java.net.InetSocketAddress
import scala.concurrent.duration._
import cats.implicits._
import cats.Monad
import java.io.File
import cats.effect.kernel.Ref

object Main extends CommandIOApp(
      name = "paf",
      header = "Protocol for Accelerated Files",
      version = "0.0.x"
    ) {

  override def main: Opts[IO[ExitCode]] = {
    (Args.clientCmd orElse Args.serverCmd).map {
      case args: ClientArgs =>
        Client.run[IO](args).map(_ => ExitCode.Success)
      case args: ServerArgs =>
        for {
          _ <- Server.run[IO](args)
        } yield ExitCode.Success
    }
  }
}
