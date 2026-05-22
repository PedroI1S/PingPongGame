package io.github.some_example_name.server;

/**
 * Headless dedicated game server entry point.
 *
 * <pre>
 *   java -jar LibGDX-Versao3-server-1.0.0.jar --port 7777 --bind 0.0.0.0
 *
 * <p>Stays running.  Clients send JOIN(bot|pvp) then CLICK packets.</p>
 * </pre>
 */
public final class ServerMain {
    public static void main(String[] args) {
        ServerOptions options = ServerOptions.parse(args);
        new GameServer().run(options);
    }

    private ServerMain() {}
}
