package com.redboxsa.controller.volley;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;

public class ArrayUtils {
    public static String mapToQueryString(HashMap<String, Object> queryString) {
        StringBuilder sb = new StringBuilder();
        try {
            for (HashMap.Entry<String, Object> e : queryString.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        return sb.toString();
    }
}

