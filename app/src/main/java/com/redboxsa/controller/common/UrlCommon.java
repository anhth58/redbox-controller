package com.redboxsa.controller.common;

import java.util.Arrays;
import java.util.List;

public class UrlCommon {

    public static final String PRODUCTION = "https://app.redboxsa.com";
    public static final String DEV = "https://dev.redboxsa.com";
    public static final String STAGE = "https://stage.redboxsa.com";

    /** Test devices: lockers follow the server their locker app uses; the N62 test terminal uses dev. Everything else is production. */
    private static final List<String> TEST_UUIDS = Arrays.asList("197c54c405e513f8", "866732032337033",
            "d01baa87f9a13f0b"); // N62 test terminal (Nearpay sandbox installed); ANDROID_ID as this app sees it

    /**
     * @param lockerServer "dev" or "stage", as handed over by the locker app (null when it has not said yet)
     * @return the server for this locker, so a test locker needs no special controller build
     */
    public static String server(String uuid, String lockerServer) {
        if (!TEST_UUIDS.contains(uuid)) return PRODUCTION;
        // A test locker whose locker app says nothing yet goes to dev: stage serves zips, not apks.
        return "stage".equals(lockerServer) ? STAGE : DEV;
    }

    /** Locker app version for this device: ?uuid= */
    public static final String CHECK_FOR_UPDATE = "/api/get-app-version";
    /** Controller (this app) version: ?current_version=&uuid= — also serves as the 30-minute ping. */
    public static final String CHECK_FOR_SELF_UPDATE = "/api/get-app-controller-version";

    // Token APIs (header "Authorization: Bearer <locker token>"), same query and response as the two above.
    // Used first when the locker has handed over a token; the two above stay as fallback.
    // TODO align the paths with backend once the endpoints exist.
    public static final String LOCKER_VERSION = "/v3/api/controller/locker-version";
    public static final String CONTROLLER_VERSION = "/v3/api/controller/controller-version";
    /** POST {"uuid", "controller_version", "logs": [EventLog entries]} */
    public static final String CONTROLLER_LOGS = "/v3/api/controller/logs";
}
