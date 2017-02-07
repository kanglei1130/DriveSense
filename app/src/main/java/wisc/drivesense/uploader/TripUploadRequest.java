package wisc.drivesense.uploader;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;

import com.android.volley.Request;
import com.android.volley.VolleyError;

import java.util.List;

import wisc.drivesense.DriveSenseApp;
import wisc.drivesense.httpPayloads.CompressedGSONRequest;
import wisc.drivesense.httpPayloads.TripPayload;
import wisc.drivesense.user.DriveSenseToken;
import wisc.drivesense.utility.Constants;
import wisc.drivesense.utility.Trip;

import static wisc.drivesense.utility.Constants.kBatchUploadCount;

/**
 * Created by Alex Sherman on 11/22/2016.
 */

public class TripUploadRequest extends CompressedGSONRequest<TripPayload> {
    private static volatile boolean running = false;
    private static volatile int failureCount = 0;
    private static final int FAILURE_THRESHOLD = 10;

    private Context context;

    /**
     * Start an upload of a trip payload that may or may not contain
     * the entire trip's points. Must indicate that an upload is in progress
     * so that race conditions don't occur
     * @param payload
     */
    public static synchronized void Start(TripPayload payload, Context context) {
        if(!running) {
            DriveSenseToken user = DriveSenseApp.DBHelper().getCurrentUser();
            if(user == null) return;
            running = true;
            TripUploadRequest currentRequest = new TripUploadRequest(Request.Method.POST, Constants.kTripURL, payload, user, context);
            DriveSenseApp.RequestQueue().add(currentRequest);
        }
    }

    /**
     * Start an upload of any past trips that aren't synced.
     * Must not start if an existing live upload is in progress.
     * @param context

     */
    public static synchronized void Start(Context context) {
        if(!running && context != null) {
            List<Trip> trips = DriveSenseApp.DBHelper().loadTrips("synced = 0 and status != 1");
            if(trips.size() == 0) return;
            boolean wifi = wifiConnected(context);
            TripPayload payload = new TripPayload();
            for (int i = 0; i < trips.size(); i++) {
                Trip trip = trips.get(i);
                payload.guid = trip.uuid.toString();
                payload.distance = trip.getDistance();
                payload.traces = DriveSenseApp.DBHelper().getUnsentTraces(payload.guid, kBatchUploadCount, !wifi);
                payload.status = trip.getStatus();
                if(payload.traces.size() > 0) {
                    //break as soon as we find a trip with traces that are uploadable given the current WiFi state
                    break;
                }
            }
            if(payload.traces.size() > 0) {
                // Only start any upload if we found a trip with uploadable traces
                Start(payload, context);
            }
        }
    }

    /**
     * Return true if the device is currently connected to WiFi
     * @param context Current application context
     * @return true if connected to WiFi
     */
    private static boolean wifiConnected(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connManager.getAllNetworks();
        boolean wifi = false;
        for (Network n: networks) {
            NetworkInfo ni = connManager.getNetworkInfo(n);
            if (ni.getType() == ConnectivityManager.TYPE_WIFI && ni.isConnected())
                wifi = true;
        }
        return wifi;
    }

    private static boolean needsUpload() {
        return DriveSenseApp.DBHelper().loadTrips("synced = 0 and status != 1").size() > 0;
    }


    private TripUploadRequest(int method, String url, TripPayload body, DriveSenseToken dsToken, Context context) {
        super(method, url, body, TripPayload.class, dsToken);
        this.context = context.getApplicationContext();
    }

    private synchronized void onComplete() {
        running = false;
        if(failureCount < FAILURE_THRESHOLD && needsUpload())
            Start(context);
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        onComplete();
        failureCount ++;
    }

    @Override
    public void onResponse(TripPayload response) {
        failureCount = 0;
        Long[] traceids = new Long[((TripPayload)payload).traces.size()];

        for (int i = 0; i < traceids.length; i++) {
            traceids[i] = ((TripPayload)payload).traces.get(i).rowid;
        }
        DriveSenseApp.DBHelper().markTracesSynced(traceids);

        // Null check is apparently necessary because java is dumb at autoboxing
        if(((TripPayload)payload).status != null && ((TripPayload)payload).status != 1 &&
                DriveSenseApp.DBHelper().getUnsentTraces(((TripPayload)payload).guid, 1).isEmpty()) {
            DriveSenseApp.DBHelper().markTripSynced(((TripPayload)payload).guid);
        }
        onComplete();
    }
}
