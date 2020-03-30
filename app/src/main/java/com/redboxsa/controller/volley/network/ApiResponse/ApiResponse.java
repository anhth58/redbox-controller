package com.redboxsa.controller.volley.network.ApiResponse;

import com.android.volley.VolleyError;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

public abstract class ApiResponse {
    protected String _raw;
    protected JSONObject _jsonObject;
    protected JSONArray _jsonArray;
    protected String _status;
    protected String _headers;

    //Generated Fields
    protected Boolean _success;
    protected Boolean _clientError;
    protected Boolean _serverError;
    protected Boolean _error;

    public String get_raw() {
        return _raw;
    }

    public void set_raw(String _raw) {
        this._raw = _raw;
    }

    public JSONObject get_jsonObject() {
        return _jsonObject;
    }

    public void set_jsonObject(JSONObject _jsonObject) {
        this._jsonObject = _jsonObject;
    }

    public JSONArray get_jsonArray() {
        return _jsonArray;
    }

    public void set_jsonArray(JSONArray _jsonArray) {
        this._jsonArray = _jsonArray;
    }

    public String get_status() {
        return _status;
    }

    public void set_status(String _status) {
        this._status = _status;
    }

    public String get_headers() {
        return _headers;
    }

    public void set_headers(String _headers) {
        this._headers = _headers;
    }

    public Boolean get_success() {
        return _success;
    }

    public void set_success(Boolean _success) {
        this._success = _success;
    }

    public Boolean get_clientError() {
        return _clientError;
    }

    public void set_clientError(Boolean _clientError) {
        this._clientError = _clientError;
    }

    public Boolean get_serverError() {
        return _serverError;
    }

    public void set_serverError(Boolean _serverError) {
        this._serverError = _serverError;
    }

    public Boolean get_error() {
        return _error;
    }

    public void set_error(Boolean _error) {
        this._error = _error;
    }

    public abstract void handleResponseSuccessed(Object object);

    public void handleNetworkResponse(String raw, String status, String headers) {
        this._jsonObject = null;
        this._raw = raw;
        this._status = status;
        this._headers = headers;
    }

    public void handleResponseError(VolleyError error) {
        this._jsonObject = null;

        if (error.networkResponse != null) {
            this._status = String.valueOf(error.networkResponse.statusCode);

            if (error.networkResponse.data != null) {
                try {
                    this._raw = new String(error.networkResponse.data, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            if (error.networkResponse.statusCode < 500) {
                this._clientError = Boolean.TRUE;
                this._serverError = Boolean.FALSE;
            } else {
                this._clientError = Boolean.FALSE;
                this._serverError = Boolean.TRUE;
            }
        } else {
            this._clientError = Boolean.TRUE;
            if(error.getMessage() != null && !error.getMessage().isEmpty()){
                this._raw = error.getMessage();
            }
        }

        this._error = Boolean.TRUE;
        this._success = Boolean.FALSE;
    }
}
