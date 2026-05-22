package io.github.some_example_name.server;

import io.github.some_example_name.network.PacketType;

/** CLI configuration for the persistent headless game server. */
public final class ServerOptions {
    public final int port;
    public final String bindAddress;

    public ServerOptions(int port, String bindAddress) {
        this.port = port;
        this.bindAddress = bindAddress;
    }

    public static ServerOptions defaults() {
        return new ServerOptions(PacketType.PORT, "0.0.0.0");
    }

    public static ServerOptions parse(String[] args) {
        int port = PacketType.PORT;
        String bind = "0.0.0.0";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (a.equals("--bind") && i + 1 < args.length) {
                bind = args[++i];
            }
        }
        return new ServerOptions(port, bind);
    }
}
