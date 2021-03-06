package wisc.drivesense.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import wisc.drivesense.user.DriveSenseToken;
import wisc.drivesense.utility.GsonSingleton;
import wisc.drivesense.utility.Trace;
import wisc.drivesense.utility.TraceMessage;
import wisc.drivesense.utility.Trip;
import wisc.drivesense.utility.TripMetadata;


public class DatabaseHelper extends SQLiteOpenHelper {

    // Logcat tag
    private static final String TAG = "DatabaseHelper";

    // Database Version
    private static final String DATABASE_NAME = "drivesense.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_USER = "user";
    private static final String TABLE_TRIP = "trip";
    private static final String TABLE_TRACE = "trace";

    // Table Create Statements
    private static final String CREATE_TABLE_USER = "CREATE TABLE IF NOT EXISTS "
            + TABLE_USER + "(email TEXT, firstname TEXT, lastname TEXT, dstoken TEXT);";

    // Synced flag on a trip ONLY indicates that the metadata for the trip has been synced
    // Trips with unsynced traces still need to be found by looking at those flags
    // uuid is used to uniquely identify a trip, it is the REAL primary key
    // it is used both on the server and device
    private static final String CREATE_TABLE_TRIP = "CREATE TABLE IF NOT EXISTS "
            + TABLE_TRIP + "(id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT, starttime INTEGER, endtime INTEGER,"
            + " distance REAL, score REAL, status INTEGER, synced INTEGER, email TEXT);";

    private static final String CREATE_TABLE_TRACE= "CREATE TABLE IF NOT EXISTS "
            + TABLE_TRACE + "(id INTEGER PRIMARY KEY AUTOINCREMENT, tripid INTEGER, type TEXT, value TEXT, synced INTEGER,"
            + " FOREIGN KEY(tripid) REFERENCES "+TABLE_TRIP+"(id));";

    //Index Create
    private static final String CREATE_INDEX_TRACE="CREATE INDEX IF NOT EXISTS i1 ON "+ TABLE_TRACE +"(tripid,type)";
    private static final String CREATE_INDEX2_TRACE="CREATE INDEX IF NOT EXISTS i2 ON "+ TABLE_TRACE +" (synced)";

    private static final String DROP_TABLE = "DROP TABLE ";

    private SQLiteDatabase wdb;
    private  SQLiteDatabase rdb;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        wdb = this.getWritableDatabase();
        rdb = this.getReadableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TRIP);
        db.execSQL(CREATE_TABLE_USER);
        db.execSQL(CREATE_TABLE_TRACE);
        db.execSQL(CREATE_INDEX_TRACE);
        db.execSQL(CREATE_INDEX2_TRACE);
    }

    /**
     * this method is called when DATABASE_VERSION is changed
     * for example, we change DATABASE_VERSION from 2 to 3
     * @oldVersion is 2 and @newVersion is 3
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Dropping tables for upgrade");
        db.execSQL(DROP_TABLE+TABLE_TRACE+";");
        db.execSQL(DROP_TABLE+TABLE_TRIP+";");
        db.execSQL(DROP_TABLE+TABLE_USER+";");
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion){

    }

    public void onOpen(SQLiteDatabase db) {
    }

    /////////////////////////////////////////////////////////////////////////////////////////

    /**
     * insert the trip meta data
     * execute at the start of a trip, mark a trip is LIVE
     * @param trip
     */
    public void insertTrip(Trip trip) {
        ContentValues values = new ContentValues();
        values.put("uuid", trip.guid.toString());
        values.put("starttime", trip.getStartTime());
        values.put("endtime", trip.getEndTime());
        values.put("distance", trip.getDistance());
        values.put("score", trip.getScore());
        values.put("status", Trip.LIVE);
        values.put("synced", 0);
        //assign to current user
        DriveSenseToken user = this.getCurrentUser();
        if(user != null) {
            values.put("email", user.email);
        } else {
            //return;
            values.put("email", "");
        }
        wdb.insert(TABLE_TRIP, null, values);
    }


    /**
     * This is for every sensor row
     * synced has been sent to the server
     * this is called only when it is sent to the server
     * it is marked synced automatically if the trip is downloaded
     * It indicates the consistence status between device and server, including download, deletion and everything
     * @param traceids
     */
    public void markTracesSynced(Long[] traceids) {
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (Long i : traceids) {
            sb.append(delim).append(i);
            delim = ",";
        }
        String whereClause = "rowid IN (" + sb.toString() +")";
        wdb.update(TABLE_TRACE, values, whereClause, null);
    }

    /**
     * This is for trip meta data
     * 1. trip sent to server
     * 2. trip downloaded
     * @param uuid
     */
    public void markTripSynced(String uuid) {
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        wdb.update(TABLE_TRIP, values, "uuid='" + uuid + "'", null);
    }

    private List<TraceMessage> cursorToTraces(Cursor cursor) {
        List<TraceMessage> res = new ArrayList<TraceMessage>();
        cursor.moveToFirst();
        do {
            if(cursor.getCount() == 0) {
                break;
            }
            String value = cursor.getString(cursor.getColumnIndex("value"));
            TraceMessage tm = GsonSingleton.fromJson(value, TraceMessage.class);
            tm.rowid = cursor.getLong(cursor.getColumnIndex("id"));
            res.add(tm);
        } while (cursor.moveToNext());
        return res;
    }

    /**
     * Return a list of trips for the current user that have unsent traces in the traces table
     * Does not include trips marked "live" (status=1). Those are only uploaded once finalized.
     *
     * @param vitalOnly Only include "important" unsent traces, to save data. (GPS only if TRUE)
     * @return list of Trips with traces marked as unsent in the database
     */
    public List<Trip> getTripsWithUnsentTraces(boolean vitalOnly) {
        DriveSenseToken user = this.getCurrentUser();
        String traceType = "";
        if(vitalOnly) traceType = " and type='"+GsonSingleton.typeNameLookup.get(Trace.Trip.class)+"'";

        String selectQuery = "SELECT " + TABLE_TRIP + ".* FROM "+TABLE_TRIP+" INNER JOIN "
                +"(SELECT tripid FROM "+TABLE_TRACE+" WHERE synced=0 "+traceType+" GROUP BY tripid) as A "+
                "ON " + TABLE_TRIP + ".`id`=`A`.`tripid`";
        if(user == null) {
            selectQuery += " WHERE email = ''";

        } else {
            selectQuery += " WHERE (email = '" + user.email + "' or email = '')";
        }
        selectQuery += "and status != 1";
        List<Trip> trips = new ArrayList<Trip>();
        Cursor cursor = rdb.rawQuery(selectQuery, null);
        cursor.moveToFirst();
        if(cursor.getCount() == 0) {
            return trips;
        }
        do {
            Trip trip = constructTripByCursor(cursor);
            trips.add(trip);
        } while (cursor.moveToNext());
        cursor.close();
        return trips;
    }

    /**
     *
     * @param uuid
     * @param limit
     * @param vitalOnly Only include "important" unsent traces, to save data. (GPS only)
     * @return
     */
    public List<TraceMessage> getUnsentTraces(String uuid, int limit, boolean vitalOnly) {
        String typeQuery = "";
        if(vitalOnly) {
            typeQuery = " and trace.type = '" + GsonSingleton.typeNameLookup.get(Trace.Trip.class)+"'";
        }
        String selectQuery = "SELECT  " + TABLE_TRACE + ".* FROM " + TABLE_TRIP + " INNER JOIN " + TABLE_TRACE
                + " on trace.tripid = trip.id WHERE trace.synced = 0"
                + typeQuery+" and trip.uuid = '" + uuid +"' LIMIT "+limit;
        Cursor cursor = rdb.rawQuery(selectQuery, null);
        List<TraceMessage> res = cursorToTraces(cursor);
        cursor.close();
        return res;
    }

    /**
     * Get the gps points of a trip, which is identified by the start time (the name of the database)
     * @param uuid the id of the trip
     * @return a list of trace, or gps points
     */
    public List<Trace.Trip> getGPSPoints(String uuid) {
        String selectQuery = "SELECT  " + TABLE_TRACE + ".* FROM " + TABLE_TRIP + " INNER JOIN " + TABLE_TRACE
                + " on trace.tripid = trip.id WHERE type = '" + Trace.Trip.class.getSimpleName() + "' and trip.uuid = '" + uuid +"' ORDER BY id ASC";
        Cursor cursor = rdb.rawQuery(selectQuery, null);
        ArrayList<Trace.Trip> res = new ArrayList<>();
        for (TraceMessage tm : cursorToTraces(cursor)) {
            res.add((Trace.Trip)tm.value);
        }
        cursor.close();
        return res;
    }

    private Trip constructTripByCursor(Cursor cursor) {
        int id = cursor.getInt(0);
        String uuid = cursor.getString(1);
        long stime = cursor.getLong(2);
        long etime = cursor.getLong(3);
        double dist = cursor.getDouble(4);
        double score = cursor.getDouble(5);
        int status = cursor.getInt(6);
        boolean synced = cursor.getInt(7)!=0;
        Trip trip = new Trip(id, uuid, stime, etime);
        trip.setScore(score);
        trip.setStatus(status);
        trip.setEndTime(etime);
        trip.setDistance(dist);
        trip.setSynced(synced);
        return trip;
    }

    /**
     * Get trip for the specificied UUID. The GPS points will not be populated,
     * and must be populated using getGPSPoints
     * @param uuid Identifier of the trip to load
     * @return
     */
    public Trip getTrip(String uuid) {
        List<Trip> trips = loadTrips("uuid='"+uuid+"'");
        if(trips.size() != 1) {
            return null;
        } else {
            return trips.get(0);
        }
    }

    public Trip getLastTrip() {
        List<Trip> unfinished = loadTrips();
        if(unfinished.size() < 1) {
            return null;
        } else {
            return unfinished.get(0);
        }
    }

    public void deleteTrip(String uuid) {
        ContentValues values = new ContentValues();
        values.put("status", TripMetadata.DELETED);
        values.put("synced", false);
        wdb.update(TABLE_TRIP, values, "uuid='" + uuid + "'", null);
    }

    /**
     * Finalize all trips if it's not null otherwise finalize all
     * trips.
     * 1. most time: it is finalize the last trip
     * 2. it is suppose to be finalize only the last trip, but it cleans up every trips possible (when there are unexpected bugs)
     */
    public void finalizeLiveTrips() {
        Log.d(TAG, "finalizing trips");
        ContentValues values = new ContentValues();
        values.put("status", Trip.FINALIZED);
        wdb.update(TABLE_TRIP, values, "status = " + Trip.LIVE, null);
    }

    /**
     * Update a whole list of trips at once with the metadata in this list.
     * Performs sparse updates (ignoring null values)
     * Called when downloading (or sync)
     * @param trips
     */
    public void updateTrips(List<TripMetadata> trips) {
        wdb.beginTransaction();
        for (TripMetadata trip:trips) {
            updateTrip(trip);
        }
        wdb.setTransactionSuccessful();
        wdb.endTransaction();
    }

    /**
     * Update a trip row using the sparse object trip. Does not set synced to false.
     * (Used to update trips when recieved from the server)
     * @param trip Members of this object that are null will not be updated in the DB
     */
    public void updateTrip(TripMetadata trip) {
        if(trip.guid == null){
            throw new Error("Trip guid was not specified");
        }
        ContentValues values = new ContentValues();
        if(trip.distance != null) values.put("distance", trip.distance);
        if(trip.status != null) values.put("status", trip.status);
        wdb.update(TABLE_TRIP, values, "uuid='" + trip.guid + "'", null);
    }

    /**
     * Update a trip in the database with all values in a Trip object
     * @param trip Values to overwrite database row with. Cannot be sparse.
     */
    public void updateTrip(Trip trip) {
        ContentValues values = new ContentValues();
        values.put("starttime", trip.getStartTime());
        values.put("endtime", trip.getEndTime());
        values.put("synced", false);
        values.put("score", trip.getScore());
        values.put("distance", trip.getDistance());
        values.put("status", trip.getStatus());
        wdb.update(TABLE_TRIP, values, "uuid='" + trip.guid + "'", null);
    }

    /**
     * Used for displaying history trips
     * @return
     */
    public List<Trip> loadTrips() { return this.loadTrips(null); }

    /**
     * Used for load trips
     * @param whereClause
     * @return
     * TODO: all sql related function should be in the database helper
     */
    public List<Trip> loadTrips(String whereClause) {

        DriveSenseToken user = this.getCurrentUser();
        List<Trip> trips = new ArrayList<>();
        String selectQuery;
        if(user == null) {
            selectQuery = "SELECT  * FROM " + TABLE_TRIP + " WHERE email = ''";

        } else {
            selectQuery = "SELECT  * FROM " + TABLE_TRIP + " WHERE (email = '" + user.email + "' or email = '')";
        }
        if(whereClause != null)
            selectQuery += " and " + whereClause;
        selectQuery += " order by starttime desc;";
        Cursor cursor = wdb.rawQuery(selectQuery, null);
        cursor.moveToFirst();
        if(cursor.getCount() == 0) {
            return trips;
        }
        do {
            Trip trip = constructTripByCursor(cursor);
            trips.add(trip);
        } while (cursor.moveToNext());
        cursor.close();
        return trips;
    }

    //email TEXT, firstname TEXT, lastname TEXT, dstoken TEXT
/* ========================== UserObject Specific Database Operations =================================== */

    /**
     * @return DriveSense token used for HTTP request, null if no user logs in
     */
    public DriveSenseToken getCurrentUser() {
        DriveSenseToken user;
        String selectQuery = "SELECT  email, firstname, lastname, dstoken FROM " + TABLE_USER;
        Cursor cursor = rdb.rawQuery(selectQuery, null);
        if(cursor.getCount() == 0) {
            return null;
        }
        cursor.moveToFirst();
        user = DriveSenseToken.InstantiateFromJWT(cursor.getString(3));
        cursor.close();
        return user;
    }


    /**
     * add a user record when the user logs in
     * remove it upon log out
     * @param token
     */
    public void userLogin(DriveSenseToken token) {
        userLogout();
        ContentValues values = new ContentValues();
        values.put("email", token.email);
        values.put("firstname", token.firstname);
        values.put("lastname", token.lastname);
        values.put("dstoken", token.jwt);
        wdb.insert(TABLE_USER, null, values);

        //Add the newly logged in user to all anonymous trips
        ContentValues tripEmail = new ContentValues();
        tripEmail.put("email", token.email);
        wdb.update(TABLE_TRIP, tripEmail, "email=''", null);
    }

    public void userLogout() {
        Log.d(TAG, "user logout processing in database");
        wdb.delete(TABLE_USER, null, null);
    }

    ////////////////////////////////////////For Trip Downloading///////////////////////////////////////////////////
    /**
     * Insert a list of TraceMessages in a bulk transaction to improve efficiency considerably
     * @param tripUUID UUID of the trip.
     * @param tmList Trace messages to insert
     * @param synced Set to true for rows that already exist on the server (i.e. recently downloaded)
     * @return List of the row IDs of the inserted objects in the order they were given
     * @throws Exception
     */
    public long[] insertSensorData(String tripUUID, List<TraceMessage> tmList, boolean synced) throws Exception {
        long[] insertIDs = new long[tmList.size()];
        String selectQuery = "SELECT id FROM " + TABLE_TRIP + " WHERE uuid = '" + tripUUID + "';";
        Cursor cursor = rdb.rawQuery(selectQuery, null);
        cursor.moveToFirst();
        if(cursor.getCount() == 0)
            throw new Exception();
        int tripID = cursor.getInt(0);
        cursor.close();
        wdb.beginTransaction();
        for (int i = 0; i < tmList.size(); i++) {
            TraceMessage tm = tmList.get(i);
            ContentValues values = new ContentValues();
            values.put("synced", synced);
            values.put("value", GsonSingleton.toJson(tm));
            values.put("type", tm.type);
            values.put("tripid", tripID);
            long rowid = wdb.insert(TABLE_TRACE, null, values);
            //rowid is an alias for a column declared as INTEGER PRIMARY KEY, which is id in this case
            //and that is what we want to return
            insertIDs[i] = rowid;
        }
        wdb.setTransactionSuccessful();
        wdb.endTransaction();
        return insertIDs;
    }

    /**
     * Insert a trip download (ONLY) from the server and all of its traces in one single transaction.
     * If any parts fails, the whole thing will be rolled back.
     * @param trip
     * @param tmList
     */
    public void insertTripAndTraces(TripMetadata trip, List<TraceMessage> tmList) {
        wdb.beginTransaction();
        ContentValues values = new ContentValues();
        values.put("uuid", trip.guid.toString());
        //TODO: Starttime and endtime should be optionally stored. Only needed when custom values are set
        if(tmList.size() > 0){
            values.put("starttime", tmList.get(0).value.time);
            values.put("endtime", tmList.get(tmList.size()-1).value.time);
        }

        values.put("distance", trip.distance);
        values.put("status", trip.status);
        values.put("synced", 1);
        DriveSenseToken user = this.getCurrentUser();
        if(user != null) {
            values.put("email", user.email);
        }
        wdb.insert(TABLE_TRIP, null, values);

        try {
            insertSensorData(trip.guid, tmList, true);
            wdb.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Something went wrong inserting traces for a downloaded trip. Rolling back.");
        }
        wdb.endTransaction();
    }

}
