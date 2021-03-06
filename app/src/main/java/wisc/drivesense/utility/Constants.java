package wisc.drivesense.utility;

/**
 * Created by lkang on 3/29/16.
 */
public class Constants {
    public static final double kEarthGravity = 9.80665; /*m^2/s*/

    /*for GPS*/
    public static final double kSmallEPSILON = 1e-8;
    public static final double kEarthRadius = 6371 * 1000; /*m*/

    public static final double kMeterToMile = 0.000621371;
    public static final double kMeterPSToMilePH = 2.23694;
    public static final double kMeterPSToKMPH = 3.6;
    public static final double kKmPHToMPH = 0.621371;
    public static final double kKmPHToMeterPS = 0.277778;
    public static final double kFeetPerMeter = 3.28084;

    public static final double kMetersPerMile = 1609.34;

    public static final double kSampleRate = 1.0;
    public static final double kRecordingInterval = 100;
    public static final int kBatchUploadCount = 5000;

    public static final int DEFAULT_MINIMUM_TRIP_DIST_METERS = 500;


    private static final String kDomain = "http://drivesense.io";

    public static final String kTripURL = kDomain + "/updateTrip";
    public static final String kTripTracesURL = kDomain + "/tripTraces";
    public static final String kAllTripsURL = kDomain + "/allTrips";

    public static final String kSignInURL = kDomain + "/auth/signin";
    public static final String kSignUpURL = kDomain + "/auth/signup";

    public static final String kGoogleSignInURL = kDomain + "/auth/google";
    public static final String kFacebookSignInURL = kDomain + "/auth/facebook";

    public static final double kExponentialMovingAverageAlpha = 0.4;//0.3;

    public static final int kNumberOfMarkerDisplay = 20;


}
