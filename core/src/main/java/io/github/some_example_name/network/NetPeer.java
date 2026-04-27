package io.github.some_example_name.network;

/** Either side of a TCP duel session. Listener callbacks fire on the GL thread. */
public interface NetPeer {

    interface Listener {
        /** Socket established and read loop is running. */
        void onConnected();
        /** A line of text arrived from the remote. Already trimmed of the trailing newline. */
        void onMessage(String line);
        /** Remote closed cleanly or the socket dropped. */
        void onDisconnected();
        /** Something went wrong before {@link #onConnected()}. */
        void onError(String reason);
    }

    /** Begin connecting/listening. Non-blocking. */
    void start();

    /** Send a single line. Newline is appended automatically. */
    void send(String line);

    /** Shut down threads and dispose sockets. Safe to call multiple times. */
    void close();
}
