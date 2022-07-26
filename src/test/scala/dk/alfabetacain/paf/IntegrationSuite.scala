package dk.alfabetacain.paf

import weaver._
import cats.effect.IO
import cats.effect.kernel.Resource
import java.util.UUID
import java.nio.file.{ Path => JPath }
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import cats.implicits._
import cats.effect.implicits._
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import cats.effect.ExitCode
import scala.io.Source

object IntegrationSuite extends SimpleIOSuite {

  private def fileName: String = "test_file_" + UUID.randomUUID().toString + ".tmp"

  private def tempFolder: Resource[IO, JPath] = {
    Resource.make(IO(Files.createTempDirectory("paf-test"))) { path =>
      Files.walk(path).sorted().toList().asScala.toList.reverse.map(f =>
        IO {
          println(s"Deleting file $f")
          f.toFile().delete()
        }
      ).sequence.map(_ => ())
    }
  }

  private def tempFile: Resource[IO, (String, JPath)] = {
    val content = "content-" + UUID.randomUUID().toString
    Resource.make(
      for {
        path <- IO(Files.createTempFile("paf-test", "tmp"))
        _    <- IO(Files.write(path, content.getBytes(StandardCharsets.UTF_8)))
      } yield (content, path)
    )(file => IO(file._2.toFile().delete()))
  }

  test("Can download file") {
    val t = (for {
      outputDir <- tempFolder
      inputDir  <- tempFolder
    } yield (outputDir, inputDir)).use { case (outputDir, inputDir) =>
      val port     = 42001
      val fileName = UUID.randomUUID().toString() ++ ".tmp"
      val content  = "hello world"
      for {
        _ <- IO(Files.write(inputDir.resolve(fileName), content.getBytes(StandardCharsets.UTF_8)))
        uploadFiber <-
          Main.run(
            List("server", "-h", "localhost", "-p", port.toString, "-f", inputDir.toString)
          ).start
        _ <- IO.sleep(1.second)
        downloadFiber <-
          Main.run(List("download", "-h", "localhost", "-p", port.toString, "-f", outputDir.toString())).start
        _ = println("Waiting for download")
        downloadExitCode <- downloadFiber.joinWith(IO.raiseError(new RuntimeException("Download was cancelled!")))
        //_ <- uploadFiber.cancel // TODO figure out why this blocks indefinitely
        _              = println("Checking file")
        filesInTempDir = outputDir.toFile().listFiles()
        downloadedFileContentOpt <- filesInTempDir.headOption.map(file =>
          IO(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).map(s => expect(s == content))
        ).fold(IO.pure(failure("No file was downloaded!")))(identity)
      } yield (expect(downloadExitCode == ExitCode.Success) and downloadedFileContentOpt)
    }
    t.timeoutTo(7.seconds, failure("Test did not complete in time").pure[IO])
  }
}
