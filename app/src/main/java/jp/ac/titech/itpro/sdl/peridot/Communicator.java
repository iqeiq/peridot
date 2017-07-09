package jp.ac.titech.itpro.sdl.peridot;


import android.util.Log;

import com.google.gson.Gson;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import static jp.ac.titech.itpro.sdl.peridot.Communicator.State.CONNECTED;
import static jp.ac.titech.itpro.sdl.peridot.Communicator.State.CONNECTING;
import static jp.ac.titech.itpro.sdl.peridot.Communicator.State.DISCONNECTED;


public class Communicator extends WebSocketListener {

    private static final String TAG = "Communicator";
    private final String host;
    private final int port;
    private WebSocket ws;
    private PublishSubject<DrawMessage> messageSubject;
    private Gson gson = new Gson();
    private String uuid = UUID.randomUUID().toString();

    private static final int NORMAL_CLOSURE_STATUS = 1000;

    enum State {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
    }
    private State state = DISCONNECTED;

    class DrawMessage {
        public String type;
        public String uuid;
        public int action;
        public float width;
        public int color;
        public float x;
        public float y;
        public DrawMessage(String type, String uuid, int action, float width, int color, float x, float y) {
            this(type, uuid);
            this.action = action;
            this.width = width;
            this.color = color;
            this.x = x;
            this.y = y;
        }
        public DrawMessage(String type, String uuid) {
            this.type = type;
            this.uuid = uuid;
        }
    }

    public Communicator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public PublishSubject<DrawMessage> connect() {
        Log.d(TAG, "connect");
        state = CONNECTING;

        OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(0,  TimeUnit.MILLISECONDS)
            .build();

        Request request = new Request.Builder()
            .url("ws://" + host + ":" + port)
            .build();

        ws = client.newWebSocket(request, this);

        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.dispatcher().executorService().shutdown();

        messageSubject = PublishSubject.create();
        return messageSubject;
    }

    public void disconnect() {
        Log.d(TAG, "disconnect");
        if(messageSubject != null) {
            messageSubject.onComplete();
            messageSubject = null;
        }
        ws.close(NORMAL_CLOSURE_STATUS, null);
        state = DISCONNECTED;
    }

    public State getState() {
        return state;
    }

    private boolean sendMessage(DrawMessage message) {
        if(state != CONNECTED) return false;
        ws.send(gson.toJson(message));
        return true;
    }

    public boolean sendDrawMessage(int action, float width, int color, float x, float y) {
        return sendMessage(new DrawMessage("draw", uuid, action, width, color, x, y));
    }

    public boolean sendClearMessage() {
        return sendMessage(new DrawMessage("clear", uuid));
    }

    @Override public void onOpen(WebSocket webSocket, Response response) {
        state = CONNECTED;
    }

    @Override public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "MESSAGE: " + text);
        DrawMessage message = gson.fromJson(text, DrawMessage.class);
        messageSubject.onNext(message);
    }

    @Override public void onClosing(WebSocket webSocket, int code, String reason) {
        disconnect();
        Log.d(TAG, "CLOSE: " + code + " " + reason);
    }

    @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        t.printStackTrace();
    }

}
