package io.github.some_example_name.network;

/**
 * Mutable listener shim so the lobby can hand a live socket to the match screen
 * without recreating the peer. All callbacks are forwarded to the current delegate.
 * Volatile ensures the swap is visible across threads.
 */
public final class PeerRouter implements NetPeer.Listener {
    private volatile NetPeer.Listener delegate;

    public void setDelegate(NetPeer.Listener d) {
        this.delegate = d;
    }

    @Override
    public void onConnected() {
        NetPeer.Listener d = delegate;
        if (d != null) d.onConnected();
    }

    @Override
    public void onMessage(String line) {
        NetPeer.Listener d = delegate;
        if (d != null) d.onMessage(line);
    }

    @Override
    public void onDisconnected() {
        NetPeer.Listener d = delegate;
        if (d != null) d.onDisconnected();
    }

    @Override
    public void onError(String reason) {
        NetPeer.Listener d = delegate;
        if (d != null) d.onError(reason);
    }
}
