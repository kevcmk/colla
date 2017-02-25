
import java.time.LocalDateTime

import better.files.File
import org.im4java.core
import org.im4java.core.{IMOperation, Info}
import org.im4java.process.ProcessStarter



object Launcher {

    def main(args: Array[String]): Unit = {

        println("Starting...")

        ProcessStarter.setGlobalSearchPath("/usr/local/opt/imagemagick@6/bin")


        import scala.pickling.Defaults._, scala.pickling.json._

        val start = java.lang.System.currentTimeMillis()


        Seq("2014, 2015, 2016").foreach { folder =>
            val base = "/Users/katz/Desktop/tria"
            val directory = File(s"${base}/${folder}")
            val total = directory.listRecursively.length.toFloat
            val pkl = directory.listRecursively.zipWithIndex.map { x =>

                val index = x._2
                val path = x._1.path.toString

                val info = new Info(path.toString)
                val dimensions = new Dimensions(info.getImageWidth, info.getImageHeight)

                val dt = (java.lang.System.currentTimeMillis() - start) / 1000.0
                val remaining = index match {
                    case 0 => 0.0
                    case _ => (total - 1 - index) * (dt / (index + 1))
                }

                println(s"$dimensions | ${folder} ${index / total.toFloat} | ${dt} | ${remaining}s remaining")
                path -> dimensions
            }.toMap.pickle

            val outFile = File(s"${base}/${folder}.pkl").write(pkl.value)

            val dict = pkl.unpickle[Map[String, Dimensions]]
            assert(dict.size == total)
            assert(dict.keys.toSeq.sorted == directory.listRecursively.map(x => x.path.toString).toSeq.sorted)

        }

//          .foreach { x =>
//            println(x.width, x.height)
//        }

        //val op : IMOperation = new IMOperation()
        //op.crop()

        //val directory =

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