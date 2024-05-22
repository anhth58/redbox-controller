package com.redboxsa.controller.common;

public class UrlCommon {

//    public static final String URL = "https://dev.redboxsa.com";
    //public static final String URL = "https://stage-sol.redboxsa.com";
    public static final String URL = "https://ga.redboxsa.com";

    public static final String UPDATE_LOCKER_STATUS = URL + "/api/update-locker-status";
    public static final String CAPTURE_IMAGE = URL + "/api/upload-captured-image";
    public static final String CAPTURE_EXCEPTION = URL + "/api/capture-exception";

    public static final String CUSTOMER_CHECK_CODE = URL + "/api/customer/check-code";
    public static final String CUSTOMER_OPEN_DOOR = URL + "/api/customer/open-door";
    public static final String CUSTOMER_OPEN_DOOR_RETURN = URL + "/api/customer/open-door-return";
    public static final String CUSTOMER_CLOSE_DOOR_RETURN = URL + "/api/customer/close-door-return";
    public static final String CUSTOMER_CLOSE_DOOR = URL + "/api/customer/close-door";
    public static final String CUSTOMER_GET_SIZE = URL + "/api/customer/get-size-return";
    public static final String CUSTOMER_CREATE_TRANSACTION = URL + "/api/customer/create-transaction-cod";

    public static final String SHIPPER_LOGIN = URL + "/api/shipper/login";
    public static final String SHIPPER_CHECK_SHIPMENT = URL + "/api/shipper/check-shipment";
    public static final String SHIPPER_GET_SIZE = URL + "/api/shipper/get-size";
    public static final String SHIPPER_GET_LOCKER = URL + "/api/get-locker";
    public static final String SHIPPER_OPEN_DOOR = URL + "/api/shipper/open-door";
    public static final String SHIPPER_CLOSE_DOOR = URL + "/api/shipper/close-door";
    public static final String SHIPPER_STATISTIC_SHIPMENT = URL + "/api/shipper/statistic-shipment";
    public static final String SHIPPER_OPEN_MULTI_DOOR = URL + "/api/shipper/open-multiple-door";
    public static final String SHIPPER_CLOSE_MULTI_DOOR = URL + "/api/shipper/close-multiple-door";
    public static final String SHIPPER_ORGANIZATION = URL + "/api/shipper/get-organizations";
    public static final String SHIPPER_CONFIRM_FINISH_DROP_OFF = URL + "/api/shipper/confirm-finish-drop-off";

    public static final String PING_LOCKER = URL + "/api/ping-locker";

    public static final String GET_LIST_POINT = URL + "/api/get-list-point";
    public static final String CREATE_LOCKER = URL + "/api/create-locker-automatically";

    public static final String VERIFY_OTP = URL + "/api/shipper/verify-otp";

    public static final String CHECK_FOR_UPDATE = URL + "/api/get-app-version";
    public static final String CHECK_FOR_SELF_UPDATE = URL + "/api/get-app-controller-version";
}
