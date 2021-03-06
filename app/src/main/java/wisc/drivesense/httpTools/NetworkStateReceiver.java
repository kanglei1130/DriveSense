package wisc.drivesense.httpTools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkStateReceiver extends BroadcastReceiver {

    private static String TAG = "NetworkStateReceiver";
    private static Intent mUploaderIntent = null;

    //This will not work from Android 7.0
    @Override
    public void onReceive(Context context, Intent intent) {

        //check internet connecction, and do uploading
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if(isConnected) {
            Log.d(TAG, "Internet is Connected!");
            TripUploadRequest.Start(context);
        } else {
            Log.d(TAG, "Internet is Closed!");
        }
    }
}
