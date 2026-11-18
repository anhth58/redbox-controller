package com.redboxsa.controller.common;

public class UrlCommon {

//    public static final String URL = "https://dev.redboxsa.com";
//    public static final String URL = "https://stage-sol.redboxsa.com";
    public static final String URL = "https://app.redboxsa.com";

    /** Locker app version for this device: ?uuid= */
    public static final String CHECK_FOR_UPDATE = URL + "/api/get-app-version";
    /** Controller (this app) version: ?current_version=&uuid= — also serves as the 30-minute ping. */
    public static final String CHECK_FOR_SELF_UPDATE = URL + "/api/get-app-controller-version";

    // Token APIs (header "Authorization: Bearer <locker token>"), same query and response as the two above.
    // Used first when the locker has handed over a token; the two above stay as fallback.
    // TODO align the paths with backend once the endpoints exist.
    public static final String LOCKER_VERSION = URL + "/v3/api/controller/locker-version";
    public static final String CONTROLLER_VERSION = URL + "/v3/api/controller/controller-version";
    /** POST {"uuid", "controller_version", "logs": [EventLog entries]} */
    public static final String CONTROLLER_LOGS = URL + "/v3/api/controller/logs";
}
