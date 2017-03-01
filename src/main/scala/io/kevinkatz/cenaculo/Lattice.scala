package io.kevinkatz.cenaculo

import java.util.Date

import better.files.File
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.im4java.core.{ConvertCmd, IMOperation, Info, MontageCmd}
import org.im4java.process.ProcessStarter

import scala.collection.JavaConverters._
import scala.pickling.Defaults._
import scala.pickling.json._
import scala.pickling.{functions, json}
import scala.util.Random


object Lattice {

    val logger = Logger("colla")

    def main(args: Array[String]): Unit = {

        logger.debug("Starting...")

        ProcessStarter.setGlobalSearchPath(ConfigFactory.load().getString("io.kevinkatz.cenaculo.imagemagick_bin"))

        val sourceDirectory = File(ConfigFactory.load().getString("io.kevinkatz.cenaculo.source_directory"))
        val destinationDirectory = File(ConfigFactory.load().getString("io.kevinkatz.cenaculo.destination.directory"))
        val destinationPrefix = ConfigFactory.load().getString("io.kevinkatz.cenaculo.destination.prefix")

        // Select the largest squares
        //            val metaSelection = metaAll.toSeq
        //              .sortBy { x => x.crop.height }
        //              .reverse
        //              .take(targetCount)

        // Select a random sample

//        Random.setSeed(42)
//        val metaSelection = metadata
//          .filter(_.dateTime.isDefined) // Filter all that had invalid datetimes
//          .filter(!_.path.contains("crop"))
//          // .filter(_.crop.width > 1280) // Filter all that are > 1280
//          .map(x => (Random.nextFloat(), x))
//          .sortBy(_._1)
//          .map(_._2)
//          .take(targetCount)

        // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._2).mkString("\n"))
        // logger.debug(metaSelection.groupBy(_.crop).map(x => x._1 -> x._2.length).toSeq.sortBy(x => x._1.width).mkString("\n"))


        //if (metaSelection.size < targetCount) {
        //    throw new IllegalArgumentException(s"Insufficent picture count [${metaSelection.size} < ${targetCount}]")
        //}
        logger.debug("Pruned to " + metaSelection.size)

        val resized: File = resizeDirectory(sub, ConfigFactory.load().getInt("io.kevinkatz.cenaculo.square_size"))
        gridDirectory(
            source=resized,
            destinationDirectory=destinationDirectory,
            aspectHorizontal = ConfigFactory.load().getInt("io.kevinkatz.cenaculo.aspect.horizontal"),
            aspectVertical = ConfigFactory.load().getInt("io.kevinkatz.cenaculo.aspect.vertical"))


    }

    def gridDirectory(source: File, destinationDirectory: File, aspectHorizontal: Int, aspectVertical: Int) = {
        if (!source.isDirectory) {
            throw new IllegalArgumentException("Source must be a directory filled with JPEGs")
        } else if (!destinationDirectory.exists || !destinationDirectory.isDirectory || !destinationDirectory.isWriteable) {
            throw new IllegalArgumentException("Destination must be a writable directory")
        }
        val cmd = new MontageCmd()
        val croppedAndResizedPaths = metadata.sortBy(_.dateTime).map(x => x.path.replace(".jpg", ".crop.256.jpg"))
        val op = new IMOperation()
        op.addRawArgs("-geometry", "+0+0")
        op.addRawArgs("-tile", "33x42")
        // op.addRawArgs("-tile", "42x33")
        // op.addRawArgs("-tile", "30x45")
        // op.addRawArgs("-tile", "45x30")
        croppedAndResizedPaths.foreach(x => op.addImage(x))
        op.addImage(sourcePath + "/montage.jpg")
        logger.debug(s"Running ${op.toString.substring(0, 256)}")
        cmd.run(op)
    }

    /* Given a path, return a temporary directory with all enclosed jpegs resized to square_size */
    def resizeDirectory(source: File, square_size: Int, resizedDirectory : Option[File]): File = {

        val outDirectory = resizedDirectory match {
            case None => File.newTemporaryDirectory()
            case Some(x) => {
                if (!x.exists) {
                    x.createDirectory()
                } else if (x.isWriteable) {
                    x
                } else {
                    throw new IllegalArgumentException("Resized Directory must be writable")
                }
            }
        }

        val cmd = new ConvertCmd();

        source.foreach { m =>

            val outPath = m.path.replace(".jpg", ".crop.256.jpg")

            if (!File(outPath).exists) {
                val crop = crop_square(m.dimensions)
                val op = new IMOperation()
                op.addImage(m.path) // Input placeholder
                op.crop(crop.width, crop.height, crop.x, crop.y)
                op.resize(square_size, square_size)
                op.addImage(outPath)
                logger.debug(s"Running ${op.toString}")
                cmd.run(op)
            } else {
                logger.debug(s"Skipping ${outPath} (exists)")
            }

        }

    }

    def depickle(path: String): Seq[ImageMetadata] = {
        functions.unpickle[Seq[ImageMetadata]](json.JSONPickle(File(path).contentAsString))
    }


    def pickleMetadata(files: Seq[String]) = {

        val start = java.lang.System.currentTimeMillis()

        val result = files.par.map { sub =>

            val threadLogger = Logger(s"colla - ${sub}")

            // val potentialDateTimePropertyNames = Seq("Properties:exif:DateTime", "Properties:exif:Datetime", "Properties:exif:datetime", "Properties:exif:DateTimeOriginal", "Properties:exif:DateTimeDigitized", "Properties:date:create")

            val directory = File(sub)

            val images = directory.listRecursively.filter(x => x.extension().get.toLowerCase == ".jpg" || x.extension().get.toLowerCase == ".jpeg").toSeq
            threadLogger.debug(s"${sub} with ${images.length} .jpg's")
            val total = images.length.toFloat

            val pkl = images.zipWithIndex.map { imageAndIndex =>

                val index = imageAndIndex._2
                val path = imageAndIndex._1.path.toString

                val metadata = getImageMetadata(path.toString)

                val dt = (java.lang.System.currentTimeMillis() - start) / 1000.0
                val remaining = index match {
                    case 0 => 0.0
                    case _ => (total - 1 - index) * (dt / (index + 1))
                }

                threadLogger.debug(s"${metadata.dimensions} | ${sub} ${index / total.toFloat} | ${metadata.dateTime} | ${dt} | ${remaining}s remaining")

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

    case class ImageMetadata(path: String, dimensions: Dimensions, dateTime: Option[Date])

    def getImageMetadata(path: String): Option[ImageMetadata] = {

        val metadata: Metadata = ImageMetadataReader.readMetadata(File(path).toJava);
        val directory: ExifSubIFDDirectory = metadata.getFirstDirectoryOfType(classOf[ExifSubIFDDirectory]);

        val TAG_DATETIME_ORIGINAL = 0x9003
        val TAG_DATETIME = 0x0132
        val TAG_DATETIME_DIGITIZED: Int = 0x9004
        val dateTime: Option[Date] = Seq(
            directory.getDateOriginal(),
            directory.getDate(TAG_DATETIME_ORIGINAL),
            directory.getDate(TAG_DATETIME),
            directory.getDate(TAG_DATETIME_DIGITIZED)
        ).filter(_ != null).headOption

        val TAG_IMAGE_WIDTH: Int = 0x0100
        val TAG_IMAGE_HEIGHT: Int = 0x0101
        val dimensions: Dimensions = (Option(directory.getInt(TAG_IMAGE_WIDTH)), Option(directory.getInt(TAG_IMAGE_HEIGHT))) match {
            case (Some(width), Some(height)) => Dimensions(width, height)
            case _ => {
                // Fallback method which actually loads image
                logger.warn(s"EXIF Dimensions Missing for image $path, retrieving manually")
                val info = new Info(path.toString)
                val dimensions = new Dimensions(info.getImageWidth, info.getImageHeight)
            }
        }

        if (dateTime.isEmpty) {
            logger.warn(s"EXIF Date Missing for image $path ")
            None
        } else {
            Some(ImageMetadata(path, dimensions, dateTime))
        }


        //val dateTimeString = potentialDateTimePropertyNames
        //  .map(x => info.getProperty(x))
        //  .filter(_ != null).headOption


    }

}