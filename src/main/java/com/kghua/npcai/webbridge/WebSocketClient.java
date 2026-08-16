package com.kghua.npcai.webbridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/**
 * 极简 WebSocket 客户端封装（Java 21 内置 java.net.http，零新依赖）。
 * 回调运行在 HttpClient 线程上，调用方需自行 marshal 回主线程。
 */
public final class WebSocketClient {
    public interface Listener {
        void onOpen();
        void onText(String text);
        void onClose();
        void onError(Throwable t);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private volatile WebSocket socket;

    public void connect(String url, String token, Listener l) {
        http.newWebSocketBuilder()
            .header("Authorization", "Bearer " + token)
            .buildAsync(URI.create(url), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    socket = webSocket;
                    webSocket.request(1);
                    l.onOpen();
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    l.onText(data.toString());
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    l.onClose();
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    l.onError(error);
                }
            })
            .join();
    }

    public boolean isOpen() {
        return socket != null && !socket.isOutputClosed();
    }

    /** WebSocket.send* 线程安全：可从主线程或调度线程调用 */
    public void send(String json) {
        WebSocket s = socket;
        if (s != null && !s.isOutputClosed()) {
            s.sendText(json, true);
        }
    }

    public void close() {
        WebSocket s = socket;
        if (s != null) {
            s.abort();
        }
    }
}
