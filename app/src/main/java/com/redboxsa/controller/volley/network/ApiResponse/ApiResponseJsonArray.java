package com.redboxsa.controller.volley.network.ApiResponse;

import org.json.JSONArray;

public class ApiResponseJsonArray extends ApiResponse {
    @Override
    public void handleResponseSuccessed(Object object) {
        this._jsonArray = (JSONArray) object;
        this._clientError = Boolean.FALSE;
        this._error = Boolean.FALSE;
        this._serverError = Boolean.FALSE;
        this._success = Boolean.TRUE;
    }
}
