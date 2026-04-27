package io.github.some_example_name.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.ServerSocket;
import com.badlogic.gdx.net.ServerSocketHints;
import com.badlogic.gdx.net.Socket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * TCP listener that accepts a single peer.
 * <p>Two background threads: an accept thread (waits for connection) and a
 * read thread (reads lines and forwards to the listener via
 * {@link com.badlogic.gdx.Application#postRunnable(Runnable)}).
 */
public final class NetHost implements NetPeer {
    private final int port;
    private final Listener listener;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedWriter out;

    public NetHost(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    @Override
    public void start() {
        running = true;
        Thread t = new Thread(this::acceptLoop, "net-host-accept");
        t.setDaemon(true);
        t.start();
    }

    private void acceptLoop() {
        try {
            ServerSocketHints hints = new ServerSocketHints();
            hints.acceptTimeout = 0; // wait forever
            serverSocket = Gdx.net.newServerSocket(Net.Protocol.TCP, port, hints);
            clientSocket = serverSocket.accept(null);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                clientSocket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(
                clientSocket.getOutputStream(), StandardCharsets.UTF_8));

            Gdx.app.postRunnable(listener::onConnected);

            Thread reader = new Thread(() -> readLoop(in), "net-host-read");
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
        try { if (clientSocket != null) clientSocket.dispose(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.dispose(); } catch (Exception ignored) {}
    }
}
