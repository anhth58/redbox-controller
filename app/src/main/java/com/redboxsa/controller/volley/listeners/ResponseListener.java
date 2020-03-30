package com.redboxsa.controller.volley.listeners;


import com.redboxsa.controller.volley.network.ApiResponse.ApiResponse;

public interface ResponseListener<T> {

    /**
     * Invoked when the request has completed its execution without errors
     *
     * @param result The resulting object from the AsyncTask.
     */
    void onRequestCompleted(ApiResponse result);


    /**
     * Invoked when the Request has completed its execution with errors
     *
     * @param error The error message.
     */
    void onRequestError(ApiResponse error);
}
