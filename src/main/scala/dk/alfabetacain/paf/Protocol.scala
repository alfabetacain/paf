package dk.alfabetacain.paf

import shapeless._
import java.io.InputStream
import dk.alfabetacain.paf.Message.FileInfo
import java.nio.charset.StandardCharsets
import java.io.OutputStream
import scodec.bits.BitVector
import scodec.bits._
import scodec.codecs._
import scodec.codecs.implicits._
import scodec.Codec
import scodec.codecs.Discriminated
import scodec.codecs.Discriminator
import scodec.Attempt
import scala.util.Try

final case class Sized[A](size: Long, value: A)

sealed trait Message

object Message {

  final case class Manifest(files: List[FileInfo])        extends Message
  final case class FileInfo(path: os.SubPath, size: Long) extends Message
  final case class Error(message: String)                 extends Message
  final case class Settings()                             extends Message
  final case class Done()                                  extends Message

  object Settings {
    def unify(s1: Settings, s2: Settings): Settings = s1
  }
}

object Format {

  implicit val subpathFormat: Codec[os.SubPath] =
    listOfN[String](int32, variableSizeBytes(int32, string(StandardCharsets.UTF_8))).exmap[os.SubPath](
      parts =>
        Attempt.fromTry(Try {
          val r = os.SubPath(parts.toIndexedSeq)
          r
        }),
      path => Attempt.successful(path.segments.toList)
    )
  // implicit val fileInfo: Codec[FileInfo]         = Codec[FileInfo]
  // implicit val error: Codec[Error]               = Codec[Error]
  // implicit val manifest: Codec[Message.Manifest] = Codec[Message.Manifest]
  // implicit val settings: Codec[Message.Settings] = Codec[Message.Settings]

  val fileInfo = implicitly[Codec[Message.FileInfo]]
  val daLong   = implicitly[Codec[Long]]

  val message: Codec[Message] =
    Codec.coproduct[Message].discriminatedByIndex(uint8)
}
