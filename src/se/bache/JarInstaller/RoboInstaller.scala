package se.bache.JarInstaller

/**
 * Created by arneball on 2013-12-19.
 */
import android.util.Log

object RoboInstaller {
  def makeWritable() {
    Log.d(TAG, "Make /system writable")
    sudo("mount -o remount,rw /system")
  }

  def makeReadOnly() {
    Log.d(TAG, "Make /system read-only")
    sudo("mount -o remount,ro /system")
  }

  def sudo(cmd: String) {
    try {
      val proc: Process = runtime.exec(Array[String]("su", "-c", cmd))
      val res: Int = proc.waitFor
      if (res != 0) throw new RuntimeException("Execution of cmd '"+cmd+"' failed with exit code "+res)
    }
    catch {
      case e: Exception => {
        throw new RuntimeException(e)
      }
    }
  }

  final val TAG: String = "RoboInstaller"
  val runtime: Runtime = Runtime.getRuntime
}