
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Date

import better.files.File
import org.im4java.core
import org.im4java.core.{ConvertCmd, IMOperation, Info, MontageCmd}
import org.im4java.process.ProcessStarter
import org.im4java.utils.{ExtensionFilter, FilenameLoader}

import scala.pickling.Defaults._
import scala.pickling.{functions, json}
import scala.pickling.json._
import scala.pickling.json.JSONPickle
import scala.util.Random
import com.typesafe.scalalogging.Logger


object Launcher {

    val logger = Logger("colla")

    def main(args: Array[String]): Unit = {

        logger.debug("Starting...")

        ProcessStarter.setGlobalSearchPath("/usr/local/opt/imagemagick@6/bin")

        val dirs = Seq(
            "/Users/katz/Desktop/tria/2014"
            // "/Users/katz/Desktop/tria/2015"
            // "/Users/katz/Desktop/tria/2016a"
        )

        // pickleMetadata(dirs)


        dirs.map { sub =>

            logger.debug(s"\n\nSub ${sub}")
            val metadata = depickle(s"${sub}.v2.pkl")

            logger.debug("Total Size " + metadata.size)

            val targetCount = 1386

            // Select the largest squares
            //            val metaSelection = metaAll.toSeq
            //              .sortBy { x => x.crop.height }
            //              .reverse
            //              .take(targetCount)

            // Select a random sample

            Random.setSeed(42)


            val metaSelection = metadata
              .filter(_.dateTime.isDefined) // Filter all that had invalid datetimes
              // .filter(_.crop.width > 1280) // Filter all that are > 1280
              .map(x => (Random.nextFloat(), x))
              .sortBy(_._1)
              .map(_._2)
              .take(targetCount)

            // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._2).mkString("\n"))
            // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._1.width).mkString("\n"))


            //if (metaSelection.size < targetCount) {
            //    throw new IllegalArgumentException(s"Insufficent picture count [${metaSelection.size} < ${targetCount}]")
            //}
            logger.debug("Pruned to " + metaSelection.size)

            val targetDirectory = s"${sub}_cropped"
            cropAndResizeDirectory(sub, metaSelection)
            gridDirectory(sub, metaSelection)

        }


    }

    def gridDirectory(sourcePath: String, metadata: Seq[ImageMetadata]) = {
        val cmd = new MontageCmd()
        val croppedAndResizedPaths = metadata.sortBy(_.dateTime).map(x => x.path.replace(".jpg", ".crop.256.jpg"))
        val op = new IMOperation()
        op.addRawArgs("-geometry", "+0+0")
        op.addRawArgs("-tile", "42x33")
        croppedAndResizedPaths.foreach(x => op.addImage(x))
        op.addImage(sourcePath + "/montage.jpg")
        logger.debug(s"Running ${op.toString.substring(0, 256)}")
        cmd.run(op)
    }

    def cropAndResizeDirectory(sourcePath: String, metadata: Seq[ImageMetadata]) = {

        // Minimum Pixel Edge Width
        val minCrop = metadata
          .groupBy(_.crop)
          .map(x => x._1 -> x._2.length)
          .toSeq
          .sortBy(x => x._1.width)
          .head
          ._1
          .width

        logger.debug(s"Cropping to square ${minCrop}")
//        val filter: ExtensionFilter = new ExtensionFilter("jpg")
//        filter.setRecursion(false);
//        val loader: FilenameLoader = new FilenameLoader(filter)
//        val images: java.util.List[String] = loader.loadFilenames(sourcePath)

        val cmd = new ConvertCmd();

        metadata.foreach { m =>
            val op = new IMOperation()
            op.addImage(m.path) // Input placeholder
            op.crop(m.crop.width, m.crop.height, m.crop.x, m.crop.y)
            op.resize(256, 256)
            op.addImage(m.path.replace(".jpg", ".crop.256.jpg"))
            logger.debug(s"Running ${op.toString}")
            cmd.run(op)
        }

    }

    def depickle(path: String): Seq[ImageMetadata] = {
        functions.unpickle[Seq[ImageMetadata]](json.JSONPickle(File(path).contentAsString))
    }


    def pickleMetadata(files: Seq[String]) = {

        val start = java.lang.System.currentTimeMillis()



        val result = files.par.map { sub =>

            val threadLogger = Logger(s"colla - ${sub}")

            val potentialDateTimePropertyNames = Seq("Properties:exif:DateTime", "Properties:exif:Datetime", "Properties:exif:datetime", "Properties:exif:DateTimeOriginal", "Properties:exif:DateTimeDigitized", "Properties:date:create")
            val formatter = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss") // Not threadsafe
            val directory = File(sub)

            val images = directory.listRecursively.filter(_.extension().get == ".jpg").toSeq
            threadLogger.debug(s"${sub} with ${images.length} .jpg's")
            val total = images.length.toFloat

            val pkl = images.zipWithIndex.map { imageAndIndex =>

                val index = imageAndIndex._2
                val path = imageAndIndex._1.path.toString

                val info = new Info(path.toString)

                val dimensions = new Dimensions(info.getImageWidth, info.getImageHeight)
                val crop = crop_square(dimensions)

                val dateTimeString = potentialDateTimePropertyNames
                  .map(x => info.getProperty(x))
                  .filter(_ != null).headOption

                val dateTime = dateTimeString match {
                    case None => {
                        threadLogger.debug(s"Couldn't find datetime for file ${path}")
                        None
                    }
                    case Some(x) => Some(formatter.parse(x))
                }

                val metadata = ImageMetadata(path, dimensions, crop, dateTime)

                val dt = (java.lang.System.currentTimeMillis() - start) / 1000.0
                val remaining = index match {
                    case 0 => 0.0
                    case _ => (total - 1 - index) * (dt / (index + 1))
                }

                threadLogger.debug(s"$dimensions | ${sub} ${index / total.toFloat} | ${dateTime} | ${dt} | ${remaining}s remaining")

                path -> metadata

            }.toMap.values.pickle

            val outFile = File(s"${sub}.v2.pkl").write(pkl.value)

            val unpickledSeq = pkl.unpickle[Seq[ImageMetadata]]
            val sortedEquivalence = unpickledSeq.map(_.path).sorted == images.map(x => x.path.toString).sorted

            threadLogger.debug(s"${directory} -- dictsize: ${unpickledSeq.size} total: ${total}")
            if (sortedEquivalence) {
                threadLogger.debug("Sorted Equivalent")
            } else {
                val dictKeys = unpickledSeq.map(_.path).sorted.mkString(",")
                val dirContents = images.map(x => x.path.toString).toSeq.sorted
                threadLogger.debug(s"Dict: ${dictKeys}\nDir :${dirContents} ")
            }
            None

        }.toList

        //logger.debug(s"Res: ${result}")

    }

    // Return the largest square crop
    def crop_square(d: Dimensions): CropBox = {
        if (d.height == d.width) {
            CropBox(d.width, d.height, 0, 0)
        } else if (d.height < d.width) {
            // Wide Image
            CropBox(d.height, d.height, (d.width - d.height) / 2, 0)
        } else {
            // Tall Image
            CropBox(d.width, d.width, 0, (d.height - d.width) / 2)
        }
    }

    case class Dimensions(width: Int, height: Int) {
        override def toString = s"$width x $height"
    }

    case class CropBox(width: Int, height: Int, x: Int, y: Int) {
        override def toString = s"$width $height $x $y"
    }

    case class ImageMetadata(path: String, dimensions: Dimensions, crop: CropBox, dateTime: Option[Date])

}