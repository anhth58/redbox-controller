package com.redboxsa.controller.volley.network;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.redboxsa.controller.volley.CustomHurlStack;

public class VolleyResourcesSingleton {
    private static VolleyResourcesSingleton mInstance;
    private static Context mCtx;
    private RequestQueue mRequestQueue;
    private RequestQueue mRequestQueueDelete;

    private VolleyResourcesSingleton(Context context) {
        mCtx = context;
        mRequestQueue = getRequestQueue();
    }

    public static synchronized VolleyResourcesSingleton getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new VolleyResourcesSingleton(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            mRequestQueue = Volley.newRequestQueue(mCtx.getApplicationContext());
        }
        return mRequestQueue;
    }

    public RequestQueue getRequestQueueDelete() {
        if (mRequestQueueDelete == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            mRequestQueueDelete = Volley.newRequestQueue(mCtx.getApplicationContext(), new CustomHurlStack());
        }
        return mRequestQueueDelete;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public <T> void addToRequestQueueDelete(Request<T> req) {
        getRequestQueueDelete().add(req);
    }
}
