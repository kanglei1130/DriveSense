package wisc.drivesense.httpTools;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.JsonSyntaxException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import wisc.drivesense.user.DriveSenseToken;
import wisc.drivesense.utility.GsonSingleton;

/**
 * Created by peter on 10/27/16.
 */

public abstract class GsonRequest<T> extends Request<T> implements Response.Listener<T>, Response.ErrorListener {
    protected Object payload;
    final String TAG = "GsonRequest";
    private final Type responseType;
    protected DriveSenseToken dsToken = null;

    public GsonRequest(int method, String url, Object body, Type responseType, DriveSenseToken dsToken) {
        super(method, url, null);
        payload = body;
        this.responseType = responseType;
        this.setRetryPolicy(new DefaultRetryPolicy(10000, 0, 0));
        this.dsToken = dsToken;
    }
    public GsonRequest(int method, String url, Object body, Class<T> responseType, DriveSenseToken dsToken) {
        this(method, url, body, (Type)responseType, dsToken);
    }

    public GsonRequest(int method, String url, Object body, Class<T> responseType) {
        this(method,url, body, (Type)responseType, null);
    }

    public String getBodyContentType()
    {
        return "application/json";
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {

        HashMap<String, String> headers = new HashMap<>(super.getHeaders());
        if(this.dsToken != null)
            headers.put("Authorization", "JWT ".concat(dsToken.jwt));
        return headers;
    }

    @Override
    public byte[] getBody() {
        String json = GsonSingleton.toJson(payload);

        return json.getBytes();
    }

    @Override
    protected void deliverResponse(T response) {
        this.onResponse(response);
    }

    @Override
    public void deliverError(VolleyError error) {
        Log.d(TAG, error.toString());
        super.deliverError(error);
        this.onErrorResponse(error);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            String json = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
            return (Response<T>)Response.success(GsonSingleton.fromJson(json, responseType),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JsonSyntaxException e) {
            return Response.error(new ParseError(e));
        }
    }
}
