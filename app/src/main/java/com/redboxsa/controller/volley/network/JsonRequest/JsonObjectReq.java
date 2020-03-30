package com.redboxsa.controller.volley.network.JsonRequest;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.redboxsa.controller.volley.ArrayUtils;
import com.redboxsa.controller.volley.listeners.ResponseListener;
import com.redboxsa.controller.volley.network.ApiResponse.ApiResponseJsonObject;
import com.redboxsa.controller.volley.network.VolleyResourcesSingleton;


import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class JsonObjectReq extends JsonRequest {

    private static HashMap<String, String> getHeaderData(Context context) {
        HashMap<String, String> header = new HashMap<>();
        header.put("Accept", "application/json");
        header.put("timezone", TimeZone.getDefault().toString());
//        header.put("authorization", AppSharedPreference.getAuthorization(context));
        header.put("accept", "application/json");
//        header.put("accept-encoding", "gzip, deflate");
        header.put("accept-language", "en-US,en;q=0.8");
        header.put("content-type", "application/json");
        header.put("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36");

        return header;
    }

    public static void makeGetRequest(final Context context,
                                      String url,
                                      HashMap<String, Object> params,
                                      JSONObject body,
                                      final ResponseListener listener) {

        final ApiResponseJsonObject apiResponse = new ApiResponseJsonObject();
        if (params != null)

            url = url.concat("?".concat(ArrayUtils.mapToQueryString(params)));
        Log.d("URL API GET: ", url);
        if (body != null) Log.d("URL API GET: ", body.toString());
        final String finalUrl = url;
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.GET,
                finalUrl,
                body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        if (jsonObject != null && !jsonObject.toString().isEmpty()) {
                            Log.d("#RES", jsonObject.toString());
                        }
                        apiResponse.handleResponseSuccessed(jsonObject);
                        listener.onRequestCompleted(apiResponse);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                apiResponse.handleResponseError(error);
                listener.onRequestError(apiResponse);
                if (error != null && error.getMessage() != null) {
                    Log.d("#ERROR", error.getMessage());
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                return getHeaderData(context);

            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return responseJsonObjectByParse(response, apiResponse);
            }
        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(3 * 1000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsonObjReq.setShouldCache(true);
        jsonObjReq.setTag("GET_REQUEST");
        //It's better if the queue is obtained with an app context to keep it alive while the app is in foreground.
        VolleyResourcesSingleton.getInstance(context.getApplicationContext()).addToRequestQueue(jsonObjReq);
    }

    public static void makePostRequest(final Context context,
                                       String url,
                                       HashMap<String, Object> params,
                                       JSONObject body,
                                       final ResponseListener listener
    ) {
        final ApiResponseJsonObject apiResponse = new ApiResponseJsonObject();
        if (params != null)
            url = url.concat("?".concat(ArrayUtils.mapToQueryString(params)));
        Log.d("URL API POST: ", url);
        if (body != null) Log.d("URL API POST: ", body.toString());
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        apiResponse.handleResponseSuccessed(jsonObject);

                        listener.onRequestCompleted(apiResponse);

                        if (jsonObject != null && !jsonObject.toString().isEmpty()) {
                            Log.d("#RES", jsonObject.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error != null && error.getMessage() != null) {
                    Log.d("#ERROR", error.getMessage());
                }
                apiResponse.handleResponseError(error);
                listener.onRequestError(apiResponse);

            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                return getHeaderData(context);
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return responseJsonObjectByParse(response, apiResponse);
            }

        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(3 * 1000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsonObjReq.setShouldCache(true);
        //It's better if the queue is obtained with an app context to keep it alive while the app is in foreground.
        VolleyResourcesSingleton.getInstance(context.getApplicationContext()).addToRequestQueue(jsonObjReq);
    }

    public static void makePutRequest(final Context context,
                                      String url,
                                      HashMap<String, Object> params,
                                      JSONObject body,
                                      final ResponseListener listener) {

        final ApiResponseJsonObject apiResponse = new ApiResponseJsonObject();
        if (params != null)
            url = url.concat("?".concat(ArrayUtils.mapToQueryString(params)));
        Log.d("URL API PUT: ", url);
        if (body != null) Log.d("URL API PUT: ", body.toString());
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        apiResponse.handleResponseSuccessed(jsonObject);

                        listener.onRequestCompleted(apiResponse);

                        if (jsonObject != null && !jsonObject.toString().isEmpty()) {
                            Log.d("#RES", jsonObject.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                apiResponse.handleResponseError(error);

                listener.onRequestError(apiResponse);
                if (error != null && error.getMessage() != null) {
                    Log.d("#ERROR", error.getMessage());
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                return getHeaderData(context);
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return responseJsonObjectByParse(response, apiResponse);
            }
        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(3 * 1000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsonObjReq.setShouldCache(true);
        //It's better if the queue is obtained with an app context to keep it alive while the app is in foreground.
        VolleyResourcesSingleton.getInstance(context.getApplicationContext()).addToRequestQueue(jsonObjReq);
    }

    public static void makeDeleteRequest(final Context context,
                                         String url,
                                         HashMap<String, Object> params,
                                         JSONObject body,
                                         final ResponseListener listener) {

        final ApiResponseJsonObject apiResponse = new ApiResponseJsonObject();
        if (params != null)
            url = url.concat("?".concat(ArrayUtils.mapToQueryString(params)));
        Log.d("URL API DELETE: ", url);
        if (body != null) Log.d("URL API DELETE: ", body.toString());
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.DELETE,
                url,
                body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        apiResponse.handleResponseSuccessed(jsonObject);

                        listener.onRequestCompleted(apiResponse);

                        if (jsonObject != null && !jsonObject.toString().isEmpty()) {
                            Log.d("#RES", jsonObject.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                apiResponse.handleResponseError(error);

                listener.onRequestError(apiResponse);
                if (error != null && error.getMessage() != null) {
                    Log.d("#ERROR", error.getMessage());
                }
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                return getHeaderData(context);
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return responseJsonObjectByParse(response, apiResponse);
            }
        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(3 * 1000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsonObjReq.setShouldCache(true);
        //It's better if the queue is obtained with an app context to keep it alive while the app is in foreground.
        VolleyResourcesSingleton.getInstance(context.getApplicationContext()).addToRequestQueueDelete(jsonObjReq);
    }

    public static void cancelAllGetRequest(Context context) {
        VolleyResourcesSingleton.getInstance(context.getApplicationContext()).getRequestQueue().cancelAll("GET_REQUEST");
    }
}
