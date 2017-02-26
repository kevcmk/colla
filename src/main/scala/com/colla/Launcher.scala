
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Date

import better.files.File
import org.im4java.core
import org.im4java.core.{IMOperation, Info}
import org.im4java.process.ProcessStarter
import org.im4java.utils.{ExtensionFilter, FilenameLoader}

import scala.pickling.Defaults._
import scala.pickling.{functions, json}
import scala.pickling.json._
import scala.pickling.json.JSONPickle
import scala.util.Random
import com.typesafe.scalalogging


object Launcher {

    val logger = Logger("colla")

    def main(args: Array[String]): Unit = {

        logger.debug("Starting...")

        ProcessStarter.setGlobalSearchPath("/usr/local/opt/imagemagick@6/bin")

        val dirs = Seq(
            "/Users/katz/Desktop/tria/2014a",
            "/Users/katz/Desktop/tria/2015a",
            "/Users/katz/Desktop/tria/2016a"
        )

        pickleMetadata(dirs)

        dirs.map { sub =>

            logger.debug(s"\n\nSub ${sub}")
            val metadata = depickle(s"${sub}.v2.pkl")

            logger.debug("Total Size " + metadata.size)

            // Select the largest squares
            //            val metaSelection = metaAll.toSeq
            //              .sortBy { x => x.crop.height }
            //              .reverse
            //              .take(1386)

            // Select a random sample
            val rng = Random.setSeed(42)
            val metaSelection = metadata
              .filter(_.dateTime.isDefined) // Filter all that had invalid datetimes
              .filter(_.crop.width > 1280) // Filter all that are > 1280
              .map(x => (Random.nextFloat(), x))
              .sortBy(_._1)
              .map(_._2)
              .take(1386)

            // Minimum Pixel Edge Width
            val minCrop = metaSelection
              .groupBy(_.crop)
              .map(x => x._1 -> x._2.length)
              .toSeq
              .sortBy(x => x._1.width)
              .head

            // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._2).mkString("\n"))
            // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._1.width).mkString("\n"))


            logger.debug("Pruned to " + metaSelection.size)
            logger.debug("Minimum Crop: " + minCrop._1.width)

            //              .sortBy(dim => (dim.width, dim.height))
            //              .groupBy(identity)
            //              .mapValues(_.length)
            //              .toSeq
            //              .sortBy(_._2)
            //              .map(x => s"${x._1} ${x._2}")
            //              .mkString("\n")
            // logger.debug(s"${sub}:\n${meta}")

            // cropDirectory(sub)

        }


    }

    def cropDirectory(path: String, dimensions: Map[String, Dimensions]) = {

        val filter: ExtensionFilter = new ExtensionFilter("jpg")
        filter.setRecursion(false);
        val loader: FilenameLoader = new FilenameLoader(filter)
        val images: java.util.List[String] = loader.loadFilenames(path)

        //        val cropped
        //        val minimumSize =


        val op = new IMOperation()

        op.size

        logger.debug(images)

    }

    def depickle(path: String): Seq[ImageMetadata] = {
        functions.unpickle[Seq[ImageMetadata]](json.JSONPickle(File(path).contentAsString))
    }


    def pickleMetadata(files: Seq[String]) = {

        val start = java.lang.System.currentTimeMillis()



        val result = files.par.map { sub =>

            val potentialDateTimePropertyNames = Seq("Properties:exif:DateTime", "Properties:exif:Datetime", "Properties:exif:datetime", "Properties:exif:DateTimeOriginal", "Properties:exif:DateTimeDigitized", "Properties:date:create")

            val formatter = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss") // Not threadsafe

            val directory = File(sub)

            val images = directory.listRecursively.filter(_.extension().get == ".jpg").toSeq
            logger.debug(s"${sub} with ${images.length} .jpg's")
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
                        logger.debug(s"Couldn't find datetime for file ${path}")
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

                logger.debug(s"$dimensions | ${sub} ${index / total.toFloat} | ${dateTime} | ${dt} | ${remaining}s remaining")

                metadata

            }.pickle

            val outFile = File(s"${sub}.v2.pkl").write(pkl.value)

            val unpickledSeq = pkl.unpickle[Seq[ImageMetadata]]
            val sortedEquivalence = unpickledSeq.map(_.path).sorted == images.map(x => x.path.toString).sorted

            logger.debug(s"${directory} -- dictsize: ${unpickledSeq.size} total: ${total}")
            if (sortedEquivalence) {
                logger.debug("Sorted Equivalent")
            } else {
                val dictKeys = unpickledSeq.map(_.path).sorted.mkString(",")
                val dirContents = images.map(x => x.path.toString).toSeq.sorted
                logger.debug(s"Dict: ${dictKeys}\nDir :${dirContents} ")
            }

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