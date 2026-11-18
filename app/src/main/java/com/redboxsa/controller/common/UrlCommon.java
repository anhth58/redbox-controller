package com.redboxsa.controller.common;

public class UrlCommon {

//    public static final String URL = "https://dev.redboxsa.com";
//    public static final String URL = "https://stage-sol.redboxsa.com";
    public static final String URL = "https://app.redboxsa.com";

    /** Locker app version for this device: ?uuid= */
    public static final String CHECK_FOR_UPDATE = URL + "/api/get-app-version";
    /** Controller (this app) version: ?current_version=&uuid= — also serves as the 30-minute ping. */
    public static final String CHECK_FOR_SELF_UPDATE = URL + "/api/get-app-controller-version";
}
