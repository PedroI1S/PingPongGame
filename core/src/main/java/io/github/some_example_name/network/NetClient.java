package io.github.some_example_name.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** TCP client. Mirror of {@link NetHost} on the connecting side. */
public final class NetClient implements NetPeer {
    private final String host;
    private final int port;
    private final Listener listener;
    private volatile boolean running;
    private Socket socket;
    private BufferedWriter out;

    public NetClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    @Override
    public void start() {
        running = true;
        Thread t = new Thread(this::connectLoop, "net-client-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connectLoop() {
        try {
            SocketHints hints = new SocketHints();
            hints.connectTimeout = 5000;
            socket = Gdx.net.newClientSocket(Net.Protocol.TCP, host, port, hints);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));

            Gdx.app.postRunnable(listener::onConnected);

            Thread reader = new Thread(() -> readLoop(in), "net-client-read");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            Gdx.app.postRunnable(() -> listener.onError(msg));
            close();
        }
    }

    private void readLoop(BufferedReader in) {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                final String l = line;
                Gdx.app.postRunnable(() -> listener.onMessage(l));
            }
        } catch (IOException ignored) {
            // socket closed by remote or by us
        } finally {
            Gdx.app.postRunnable(listener::onDisconnected);
        }
    }

    @Override
    public synchronized void send(String line) {
        if (out == null) return;
        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            Gdx.app.postRunnable(() -> listener.onError("send failed: " + e.getMessage()));
        }
    }

    @Override
    public void close() {
        running = false;
        try { if (socket != null) socket.dispose(); } catch (Exception ignored) {}
    }
}
