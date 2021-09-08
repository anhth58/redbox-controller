package com.redboxsa.controller.common;

import com.github.nkzawa.engineio.client.transports.WebSocket;
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
                IO.Options options = new IO.Options();
                options.transports = new String[]{WebSocket.NAME};
//            options.query = "token=my custom token";

                socket = IO.socket(UrlCommon.URL,options);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return socket;
    }
}
