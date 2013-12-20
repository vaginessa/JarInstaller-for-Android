package se.bache.JarInstaller;

import android.os.AsyncTask;
import android.util.Log;
import scala.*;
import scala.runtime.BoxedUnit;

import java.io.*;
import java.net.*;

/**
 * Created by arneball on 2013-12-19.
 */
public abstract class MyAsync extends AsyncTask<Void, Integer, File> {
    private final String url;
    private final File file;

    public MyAsync(String url, File file) {
        this.url = url;
        this.file = file;
    }

    @Override
    protected File doInBackground(Void... params) {
        InputStream in = null;
        OutputStream out = null;
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
            int length = urlConnection.getContentLength();
            in = urlConnection.getInputStream();
            out = new FileOutputStream(file);
            int count;
            int total = 0;
            byte[] buffer = new byte[8192];
            while ((count = in.read(buffer)) > 0) {
                total += count;
                out.write(buffer, 0, count);
                double percent = total / (double)length;
                Log.e("SHIT", "total = " + total + " length = " + length + " percent = " + percent);
                publishProgress((int)(100 * percent));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                in.close();
            }catch (Exception e) {}
            try {
                out.close();
            }catch (Exception e) {}
        }
        return file;
    }

    public abstract void update(int update);

    @Override
    protected void onProgressUpdate(Integer... values) {
        Log.e("Setting progress", Integer.toString(values[0]));
        update(values[0]);
    }
}
