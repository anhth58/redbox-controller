package com.redboxsa.controller.common;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.net.URISyntaxException;

public class SocketController {
    private static Socket socket;
    public static boolean firstTry = true;
    private SocketController() {};
    public static Socket getSocket() {
        if (socket == null) {
            try {
                socket = IO.socket(UrlCommon.URL);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return socket;
    }
}
