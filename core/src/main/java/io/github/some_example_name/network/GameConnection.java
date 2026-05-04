package io.github.some_example_name.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executor;

/**
 * Binary-protocol wrapper around a TCP {@link Socket}.
 *
 * <p>A background reader thread ingests packets and dispatches typed callbacks
 * through the supplied {@link Executor}.  All send methods are synchronized and
 * safe to call from any thread.</p>
 *
 * <h3>Obtaining an instance</h3>
 * <ul>
 *   <li>{@link #connect} — creates the object immediately and connects on a
 *       daemon thread.  The returned reference is valid before the socket is
 *       open; store it right away, callbacks arrive later on the dispatch thread.</li>
 *   <li>{@link #wrap} — synchronous factory for server-side accepted sockets.</li>
 * </ul>
 *
 * <h3>Dispatch conventions</h3>
 * <ul>
 *   <li>Clients pass {@code Gdx.app::postRunnable} so callbacks land on the GL thread.</li>
 *   <li>The server passes {@code Runnable::run} (immediate, on the reader thread).</li>
 * </ul>
 */
public final class GameConnection {

    // ── Listener ──────────────────────────────────────────────────────────────

    /** All methods have empty default implementations — implement only what you need. */
    public interface Listener {
        // Server → Client
        default void onWaiting()                                                  {}
        default void onWelcome(int playerNumber)                                  {}
        default void onState(float px, float py, float pz,
                             float vx, float vy, float vz,
                             int p1lives, int p2lives,
                             boolean ballVisible, int activePlayer)                {}
        default void onGameOver(int winnerPlayer)                                 {}
        default void onSfx(int sfxType)                                           {}
        // Client → Server
        default void onHello()                                                    {}
        default void onServe()                                                    {}
        default void onHit(float vx, float vy, float vz)                         {}
        default void onBye()                                                      {}
        // Connection lifecycle
        default void onDisconnected()                                             {}
        default void onError(String reason)                                       {}
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Executor         dispatch;
    private volatile Listener      listener;
    private volatile Socket        socket;
    private volatile DataOutputStream out;
    private volatile boolean       closed;

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Creates a {@link GameConnection} and begins connecting to {@code host:port}
     * on a daemon thread.  The returned object is valid immediately — store it
     * before the first callback can fire on the dispatch thread.
     *
     * <p>On success the reader loop starts automatically.
     * On failure {@link Listener#onError} is called via {@code dispatch}.</p>
     */
    public static GameConnection connect(String host, int port,
                                         Executor dispatch, Listener listener) {
        GameConnection conn = new GameConnection(dispatch, listener);
        Thread t = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 8000);
                s.setTcpNoDelay(true);
                conn.socket = s;
                conn.out    = new DataOutputStream(s.getOutputStream());
                conn.startReader(s);
            } catch (IOException e) {
                if (!conn.closed) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    dispatch.execute(() -> conn.listener.onError(msg));
                }
            }
        }, "gc-connect");
        t.setDaemon(true);
        t.start();
        return conn;
    }

    /**
     * Wraps an already-accepted server socket; starts the reader immediately.
     * Pass {@code Runnable::run} as the dispatch executor from within the server.
     */
    public static GameConnection wrap(Socket socket, Executor dispatch, Listener listener)
            throws IOException {
        socket.setTcpNoDelay(true);
        GameConnection conn = new GameConnection(dispatch, listener);
        conn.socket = socket;
        conn.out    = new DataOutputStream(socket.getOutputStream());
        conn.startReader(socket);
        return conn;
    }

    private GameConnection(Executor dispatch, Listener listener) {
        this.dispatch = dispatch;
        this.listener = listener;
    }

    // ── Listener hot-swap ─────────────────────────────────────────────────────

    /**
     * Replaces the active listener atomically.  Safe to call from any thread;
     * the next dispatch cycle picks up the new reference.
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ── Reader ────────────────────────────────────────────────────────────────

    private void startReader(Socket s) {
        Thread t = new Thread(() -> readLoop(s), "gc-read-" + s.getRemoteSocketAddress());
        t.setDaemon(true);
        t.start();
    }

    private void readLoop(Socket s) {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            //noinspection InfiniteLoopStatement
            while (true) {
                byte type = in.readByte();
                switch (type) {
                    case PacketType.WAITING -> dispatch.execute(() -> listener.onWaiting());
                    case PacketType.WELCOME -> {
                        int pn = in.readByte() & 0xFF;
                        dispatch.execute(() -> listener.onWelcome(pn));
                    }
                    case PacketType.STATE -> {
                        float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
                        float vx = in.readFloat(), vy = in.readFloat(), vz = in.readFloat();
                        int   p1l = in.readInt(),  p2l = in.readInt();
                        boolean bv = in.readByte() != 0;
                        int   ap  = in.readByte() & 0xFF;
                        dispatch.execute(
                            () -> listener.onState(px, py, pz, vx, vy, vz, p1l, p2l, bv, ap));
                    }
                    case PacketType.GAME_OVER -> {
                        int winner = in.readByte() & 0xFF;
                        dispatch.execute(() -> listener.onGameOver(winner));
                    }
                    case PacketType.SFX -> {
                        int sfxType = in.readByte() & 0xFF;
                        dispatch.execute(() -> listener.onSfx(sfxType));
                    }
                    case PacketType.HELLO -> dispatch.execute(() -> listener.onHello());
                    case PacketType.SERVE -> dispatch.execute(() -> listener.onServe());
                    case PacketType.HIT   -> {
                        float vx = in.readFloat(), vy = in.readFloat(), vz = in.readFloat();
                        dispatch.execute(() -> listener.onHit(vx, vy, vz));
                    }
                    case PacketType.BYE   -> dispatch.execute(() -> listener.onBye());
                    default -> { /* unknown type — cannot recover without length prefix */ }
                }
            }
        } catch (EOFException | SocketException ignored) {
            // Normal closure.
        } catch (IOException e) {
            if (!closed) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                dispatch.execute(() -> listener.onError(msg));
                return;
            }
        }
        if (!closed) dispatch.execute(() -> listener.onDisconnected());
    }

    // ── Send — Server → Client ────────────────────────────────────────────────

    public void sendWaiting() {
        write(() -> out.writeByte(PacketType.WAITING));
    }

    public void sendWelcome(int playerNumber) {
        write(() -> { out.writeByte(PacketType.WELCOME); out.writeByte(playerNumber); });
    }

    public void sendState(float px, float py, float pz,
                          float vx, float vy, float vz,
                          int p1lives, int p2lives,
                          boolean ballVisible, int activePlayer) {
        write(() -> {
            out.writeByte(PacketType.STATE);
            out.writeFloat(px); out.writeFloat(py); out.writeFloat(pz);
            out.writeFloat(vx); out.writeFloat(vy); out.writeFloat(vz);
            out.writeInt(p1lives);
            out.writeInt(p2lives);
            out.writeByte(ballVisible ? 1 : 0);
            out.writeByte(activePlayer);
        });
    }

    public void sendGameOver(int winnerPlayer) {
        write(() -> { out.writeByte(PacketType.GAME_OVER); out.writeByte(winnerPlayer); });
    }

    public void sendSfx(int sfxType) {
        write(() -> { out.writeByte(PacketType.SFX); out.writeByte(sfxType); });
    }

    // ── Send — Client → Server ────────────────────────────────────────────────

    public void sendHello() {
        write(() -> out.writeByte(PacketType.HELLO));
    }

    public void sendServe() {
        write(() -> out.writeByte(PacketType.SERVE));
    }

    public void sendHit(float vx, float vy, float vz) {
        write(() -> {
            out.writeByte(PacketType.HIT);
            out.writeFloat(vx); out.writeFloat(vy); out.writeFloat(vz);
        });
    }

    public void sendBye() {
        write(() -> out.writeByte(PacketType.BYE));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void close() {
        if (closed) return;
        closed = true;
        Socket s = socket;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    public boolean isClosed() { return closed; }

    // ── Internal ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface IoTask { void run() throws IOException; }

    private void write(IoTask task) {
        if (closed) return;
        DataOutputStream o = out;
        if (o == null) return; // not yet connected
        try {
            synchronized (o) {
                task.run();
                o.flush();
            }
        } catch (IOException e) {
            close();
        }
    }
}
