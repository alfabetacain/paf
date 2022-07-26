package dk.alfabetacain.paf

import cats.implicits._
import cats.effect.kernel.Sync
import cats.effect.kernel.Resource
import java.io.File
import os.Path

trait WatchedFolder[F[_]] {
  def getFiles(): F[List[(os.Path, Message.FileInfo)]]
}

object WatchedFolder {

  def makeStatic[F[_]: Sync](root: os.Path): Resource[F, WatchedFolder[F]] = {
    for {
      files <- Resource.eval(Sync[F].interruptible(root.toIO.listFiles()))
      found <- Resource.eval(files.toList.map(findChildren[F](os.SubPath.sub, _)).sequence.map(_.flatten))
      _ <- Resource.eval(scribe.cats[F].info(s"Found files = ${found.mkString("\n")}"))
    } yield new WatchedFolder[F] {
      override def getFiles(): F[List[(Path, Message.FileInfo)]] = Sync[F].pure(found)
    }
  }

  private def findChildren[F[_]: Sync](prefix: os.SubPath, file: File): F[List[(os.Path, Message.FileInfo)]] = {
    for {
      isDirectory <- Sync[F].interruptible(file.isDirectory())
      result <-
        if (isDirectory) {
          val newPrefix = prefix / file.getName()
          for {
            children <-
              Sync[F].interruptible(file.listFiles()).map(files => Option(files).map(_.toList).getOrElse(List.empty))
            resolvedChildren <- children.map(findChildren(newPrefix, _)).sequence.map(_.flatten)
          } yield resolvedChildren
        } else {
          Sync[F].interruptible(file.length()).map(length =>
            List((os.Path(file), Message.FileInfo(prefix / file.getName(), length)))
          )
        }
    } yield result
  }
}
