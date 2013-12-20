package se.bache.JarInstaller
import scala.concurrent.ExecutionContext.Implicits.global
import android.app.Activity
import android.os.{AsyncTask, Bundle}
import android.view.View
import android.view.View.OnClickListener
import java.io._
import android.widget.{Toast, TextView}
import java.net.{URLEncoder, URL, HttpURLConnection}
import android.util.Log
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import scala.concurrent.{Promise, Future}
import org.json.{JSONArray, JSONObject}
import scala.util.Try

/**
 * Created by arneball on 2013-12-19.
 */
object Neger {
  def copyStream(inputStream: InputStream, outputStream: OutputStream): Unit = {
    val bin = new BufferedInputStream(inputStream)
    val bos = new BufferedOutputStream(outputStream)
    var byte = 0
    def cond = {
      byte = bin.read()
      byte != -1
    }
    while(cond) bos.write(byte)
    bin.close()
    bos.close()
  }

  implicit class FileW(val f: File) extends AnyVal {
    def cp(target: File) = copyStream(new FileInputStream(f), new FileOutputStream(target))
  }

  /** Pimps string with some nice methods */
  implicit class Strw(val str: String) extends AnyVal {
    /** url encode the string */
    def urlEncode = URLEncoder.encode(str, "utf-8")

    /** Copy the string to a file */
    def toFile(file: File) = copyStream(toStream, new FileOutputStream(file))

    /** Create a inputstream from the String */
    def toStream = new InputStream {
      private[this]val strlen = str.length
      var cnt = 0
      def read(): Int = cnt match {
        case `strlen` => -1
        case n =>
          val tmp = str(cnt)
          cnt += 1
          tmp
      }
    }
  }

  implicit def arr2seq(js: JSONArray): List[String] = {
    (0 until js.length toList).map{ js.getString }
  }
}
class MyActivity extends Activity {
  import Neger._
  def toast(mess: => Any) = uiThread{
    Log.e("BATIK", mess.toString)
    Toast.makeText(this, mess.toString, Toast.LENGTH_LONG).show()
  }

  def uiThread(fun: =>Unit) = runOnUiThread{
    new Runnable{
      def run = fun
    }
  }

  def thread[T](fun: =>T): Future[T] = {
    val p = Promise[T]()
    new Thread{
      override def run = try{
        p.success(fun)
      } catch {
        case t: Throwable => p.failure(t)
      }
    }.start()
    p.future
  }

  override def onCreate(b: Bundle) = {
    super.onCreate(b)
    setContentView(R.layout.main)
    findViewById(R.id.button).setOnClickListener(new OnClickListener {
      def onClick(v: View): Unit = {
        val libname = findViewById(R.id.lib_name).asInstanceOf[TextView].getText.toString
        if(libname.isEmpty) {
          toast{ "Choose lib name" }
          return
        }
        val url = findViewById(R.id.editText).asInstanceOf[TextView].getText.toString
        for {
          refs <- getDexedRefs(url)
          dexedFiles <- getDexedJars(refs)
        } {
          toast("got all files")
          RoboInstaller.makeWritable()
          dexedFiles.zipWithIndex.foreach{ case (file, index) =>
            val newFileName = libname + index + ".jar"
            file.cp(new File(getFilesDir, newFileName))
            Log.e("File mapping", s"${file.getName} -> ${newFileName}}")
            val xmlContent = xmlTemplate(libname, index)
            val xmlFile = new File(getFilesDir, s"$libname$index.xml")
            xmlContent.toFile(xmlFile)
            Try{
              RoboInstaller.sudo("ln -s "+xmlFile.getAbsolutePath+" /system/etc/permissions/"+xmlFile.getName)
            }
            RoboInstaller.sudo("chmod 644 " + xmlFile.getAbsolutePath)
            toast(s"$newFileName done")
          }
          RoboInstaller.makeReadOnly()
          toast("All files copied and permissions are done")
        }
      }
    })

    def getDexedRefs(url: String): Future[List[String]] = thread{
      val res = new DefaultHttpClient().execute(new HttpGet(s"http://10.0.1.12:9000/musta/${url.urlEncode}"))
      val out = new StringOutputStream
      copyStream(res.getEntity.getContent, out)
      toast("got dexed refs")
      Log.d("JSON Input", out.toString)
      new JSONObject(out.toString).getJSONArray("files"): List[String]
    }

    def getDexedJars(refs: List[String]): Future[List[File]] = {
      val futures: List[Future[File]] = for {
        ref <- refs
      } yield url2file(ref)
      toast("created files future")
      Future.sequence(futures)
    }

    def url2file(url: String): Future[File] = thread{
      val res = new DefaultHttpClient().execute(new HttpGet(s"http://10.0.1.12:9000/get/${url.urlEncode}"))
      val thatFile = File.createTempFile("mustivar", "slask")
      copyStream(res.getEntity.getContent, new FileOutputStream(thatFile))
      Log.e("FileName", thatFile.getAbsolutePath)
      thatFile
    }
  }

  def xmlTemplate(libname: String, number: Int) = s"""
  <permissions>
    <library name="$libname$number"
             file="/data/data/se.bache.JarInstaller/files/$libname$number.jar"/>
  </permissions>"""
}

class StringOutputStream extends OutputStream {
  val sb = new StringBuilder
  def write(oneByte: Int): Unit = sb.append(oneByte.toChar)
  override def toString = sb.toString()
}