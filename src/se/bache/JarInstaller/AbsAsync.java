package se.bache.JarInstaller;

import android.os.AsyncTask;
import scala.*;

/**
 * Created by arneball on 2013-12-21.
 */
public abstract class AbsAsync<Out> extends AsyncTask<Void, Void, Out> {

    @Override
    protected final Out doInBackground(Void... params) {
        return asyncWork();
    }

    public abstract Out asyncWork();
}
