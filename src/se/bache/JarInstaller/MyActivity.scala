package se.bache.JarInstaller
import scala.concurrent.ExecutionContext.Implicits.global
import android.app.{AlertDialog, Activity}
import android.os.{AsyncTask, Bundle}
import android.view.View
import android.view.View.OnClickListener
import java.io._
import android.widget._
import java.net.{URLEncoder, URL, HttpURLConnection}
import android.util.Log
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet
import scala.concurrent.{Promise, Future}
import org.json.{JSONArray, JSONObject}
import scala.util.Try
import java.util.zip.ZipFile
import android.widget.AdapterView.OnItemClickListener
import android.app.AlertDialog.Builder

/**
 * Created by arneball on 2013-12-19.
 */
object Helpers {

  def host = "http://jardexer.bache.se:9000"
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
    def toFile(file: File) = copyStream(toInputStream, new FileOutputStream(file))

    /** Create a inputstream from the String */
    def toInputStream = new InputStream {
      private[this]val strlen = str.length
      private[this]var cnt = 0
      def read(): Int = cnt match {
        case `strlen` => -1
        case n =>
          val tmp = str(cnt)
          cnt += 1
          tmp
      }
    }
  }
}

class MyActivity extends Activity {
  import Helpers._
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
      override def run = p.tryComplete(Try{
       fun
      })
    }.start()
    p.future
  }

  private def getTextV(resid: Int) = findViewById(resid).asInstanceOf[TextView].getText.toString
  private def libname = getTextV(R.id.lib_name)
  private def url = getTextV(R.id.editText)

  def downloadDexedStuff(v: View): Unit = libname match {
    case libname if libname.isEmpty => toast{ "Choose lib name" }
    case libname =>
      getTurboMust(url).foreach{ dexedFiles =>
        toast("got all files")
        RoboInstaller.makeWritable()
        val newLibNames = dexedFiles.zipWithIndex.map{ case (file, index) =>
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
          s"""<uses-library android:name="$libname$index">"""
        }
        RoboInstaller.makeReadOnly()
        toast("All files copied and permissions are done")

        // pop a dialog also
        uiThread{
          new Builder(this).setTitle("Congratulations").setView(new TextView(this){
            val libnamespretty = newLibNames.mkString("\n")
            setText{
              s"""You can now refer to
              | ${libnamespretty}
              | in your manifest""".stripMargin
            }
          }).create().show()
        }
      }
  }

  override def onCreate(b: Bundle) = {
    super.onCreate(b)
    setContentView(R.layout.main)
  }

  def setProgress(mess: Any) = uiThread{
    findViewById(R.id.percent).asInstanceOf[TextView].setText(mess.toString)
  }

  import scala.collection.JavaConversions._
  def getTurboMust(url: String): Future[List[File]] = {
    val p = Promise[List[File]]()
    val thatUrl = s"$host/musta/${url.urlEncode}"
    val file = new File(getCacheDir, "tmp.zip")
    new MyAsync(thatUrl, file){
      def update(update: Int): Unit = setProgress(s"$update % downloaded")
      override def onPostExecute(file: File) = {
        val zipped = new ZipFile(file)
        val inputstreams = zipped.entries().filter{ _.getName.endsWith("jar") }.map{ zipped.getInputStream }
        val listOfFiles = inputstreams.map{ inputStream =>
          val tmpFile = mkTemp()
          copyStream(inputStream, new FileOutputStream(tmpFile))
          tmpFile
        }.toList
        p.success(listOfFiles)
        file.delete()
      }
    }.execute()

    p.future
  }

  def mkTemp() = File.createTempFile("mustivar", "slask")
  def xmlTemplate(libname: String, number: Int) = s"""
  <permissions>
    <library name="$libname$number"
             file="/data/data/se.bache.JarInstaller/files/$libname$number.jar"/>
  </permissions>"""
}

class AsyncS[I](fun: => I)(callback: I => Unit) extends AbsAsync[I] {
  def asyncWork(): I = fun
  override def onPostExecute(i: I) = callback(i)
  execute() // start it in the constructor
}