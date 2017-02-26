
import java.time.LocalDateTime

import better.files.File
import org.im4java.core
import org.im4java.core.{IMOperation, Info}
import org.im4java.process.ProcessStarter



object Launcher {

    def main(args: Array[String]): Unit = {

        println("Starting...")

        ProcessStarter.setGlobalSearchPath("/usr/local/opt/imagemagick@6/bin")

        pickleDimensions()


    }


    def pickleDimensions() = {

        import scala.pickling.Defaults._, scala.pickling.json._
        val start = java.lang.System.currentTimeMillis()
        val base = "/Users/katz/Desktop/tria"

        val result = Seq("2014a", "2015a", "2016a").par.map { sub =>

            val directory = File(s"${base}/${sub}")

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

            val outFile = File(s"${base}/${sub}.pkl").write(pkl.value)

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

    case class Dimensions(width: Int, height: Int)

    case class CropBox(width: Int, height: Int, x: Int, y: Int)

}