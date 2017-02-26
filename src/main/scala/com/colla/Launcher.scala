
import java.nio.file.Path
import java.time.LocalDateTime

import better.files.File
import org.im4java.core
import org.im4java.core.{IMOperation, Info}
import org.im4java.process.ProcessStarter

import scala.pickling.Defaults._
import scala.pickling.{functions, json}
import scala.pickling.json._
import scala.pickling.json.JSONPickle



object Launcher {

    def main(args: Array[String]): Unit = {

        println("Starting...")

        ProcessStarter.setGlobalSearchPath("/usr/local/opt/imagemagick@6/bin")

        val dirs = Seq(
            "/Users/katz/Desktop/tria/2014a",
            "/Users/katz/Desktop/tria/2015a",
            "/Users/katz/Desktop/tria/2016a"
        )

        pickleDimensions(dirs)

        val dimensionMaps = dirs.map { sub =>
            sub -> depickle(s"${sub}.pkl")
        }.toMap

        val dimensionSets = dimensionMaps.mapValues { x => x.values.toSet }

        println(dimensionSets.map { x =>
            val res = x._2.map { dimensions =>
                s"${dimensions} -> ${crop_square(dimensions)}"
            }.mkString("\n")
            s"${x._1}:\n${res}"
        }.mkString("\n\n"))


    }

    def depickle(path: String) : Map[String, Dimensions] = {
        functions.unpickle[Map[String, Dimensions]](json.JSONPickle(File(path).contentAsString))
    }


    def pickleDimensions(files: Seq[String]) = {

        val start = java.lang.System.currentTimeMillis()

        val result = files.par.map { sub =>

            val directory = File(sub)

            val images = directory.listRecursively.filter(_.extension().get == ".jpg").toSeq
            println(s"${sub} images: " + images.mkString(", "))

            val total = images.length.toFloat
            val pkl = images.zipWithIndex.map { x =>

                val index = x._2
                val path = x._1.path.toString

                val info = new Info(path.toString)
                val dimensions = new Dimensions(info.getImageWidth, info.getImageHeight)

                val dt = (java.lang.System.currentTimeMillis() - start) / 1000.0
                val remaining = index match {
                    case 0 => 0.0
                    case _ => (total - 1 - index) * (dt / (index + 1))
                }

                println(s"$dimensions | ${sub} ${index / total.toFloat} | ${dt} | ${remaining}s remaining")
                path -> dimensions
            }.toMap.pickle

            val outFile = File(s"${sub}.pkl").write(pkl.value)

            val dict = pkl.unpickle[Map[String, Dimensions]]
            val sortedEquivalence = dict.keys.toSeq.sorted == images.map(x => x.path.toString).toSeq.sorted

            println(s"${directory} -- dictsize: ${dict.size} total: ${total}")
            if (sortedEquivalence) {
                println("Sorted Equivalent")
            } else {
                val dictKeys = dict.keys.toSeq.sorted.mkString(",")
                val dirContents = images.map(x => x.path.toString).toSeq.sorted
                println(s"Dict: ${dictKeys}\nDir :${dirContents} ")
            }

        }.toList

        //println(s"Res: ${result}")

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

}