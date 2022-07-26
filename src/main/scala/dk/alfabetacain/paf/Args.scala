package dk.alfabetacain.paf

import cats.implicits._
import com.monovore.decline._
import java.io.File
import java.nio.file.{ Path => JPath }

case class ServerArgs(
    host: String,
    port: Int,
    sourceFolder: os.Path
)

case class ClientArgs(
    host: String,
    port: Int,
    outputFolder: os.Path
)

object Args {

  private val portOpt = Opts.option[Int]("port", short = "p", help = "Port")

  def serverCmd = {
    val hostOpt = Opts.option[String]("host", short = "h", help = "Host to upload to").withDefault("0.0.0.0")
    val sourceFileOpt =
      Opts.option[JPath]("folder", short = "f", help = "Folder to server").map(p => os.Path(p)).validate(
        "folder must be a folder"
      )(_.toIO.isDirectory())
    Opts.subcommand("server", "Serves source-folder") {
      (hostOpt, portOpt, sourceFileOpt).mapN(ServerArgs.apply)
    }
  }

  def clientCmd = {
    val hostOpt = Opts.option[String]("host", short = "h", help = "Host to download from")
    val outputFolderOpt =
      Opts.option[JPath]("folder", short = "f", help = "Folder to output downloaded file(s) to").map(p =>
        os.Path(p)
      ).validate("Output folder must be a folder")(_.toIO.isDirectory())

    Opts.subcommand("download", "Download file(s)") {
      (hostOpt, portOpt, outputFolderOpt).mapN(ClientArgs.apply)
    }
  }
}
