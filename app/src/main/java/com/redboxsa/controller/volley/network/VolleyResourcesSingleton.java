package com.redboxsa.controller.volley.network;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.collection.LruCache;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.redboxsa.controller.volley.CustomHurlStack;
import com.redboxsa.controller.volley.VolleyToolboxExtension;
import com.redboxsa.controller.volley.formData.LruBitmapCache;


public class VolleyResourcesSingleton {
    private static VolleyResourcesSingleton mInstance;
    private static Context mCtx;
    private RequestQueue mRequestQueue;
    private RequestQueue mRequestQueueDelete;
    private ImageLoader mImageLoader;
    private VolleyResourcesSingleton(Context context) {
        mCtx = context;
        mRequestQueue = getRequestQueue();

        mImageLoader = new ImageLoader(mRequestQueue,
                new ImageLoader.ImageCache() {
                    private final LruCache<String, Bitmap> cache = new LruBitmapCache(mCtx);

                    @Override
                    public Bitmap getBitmap(String url) {
                        return cache.get(url);
                    }

                    @Override
                    public void putBitmap(String url, Bitmap bitmap) {
                        cache.put(url, bitmap);
                    }
                });
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
//            mRequestQueue = Volley.newRequestQueue(mCtx.getApplicationContext());
            mRequestQueue = VolleyToolboxExtension.newRequestQueue(mCtx.getApplicationContext());
        }
        return mRequestQueue;
    }

    public RequestQueue getRequestQueueDelete() {
        if (mRequestQueueDelete == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
//            mRequestQueueDelete = Volley.newRequestQueue(mCtx.getApplicationContext(), new CustomHurlStack());
            mRequestQueueDelete = VolleyToolboxExtension.newRequestQueue(mCtx.getApplicationContext(), new CustomHurlStack());
        }
        return mRequestQueueDelete;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public <T> void addToRequestQueueDelete(Request<T> req) {
        getRequestQueueDelete().add(req);
    }

    /**
     * Get image loader.
     *
     * @return ImageLoader
     */
    public ImageLoader getImageLoader() {
        return mImageLoader;
    }
}
