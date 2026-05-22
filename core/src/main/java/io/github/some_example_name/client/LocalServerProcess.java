package io.github.some_example_name.client;

import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.network.PacketType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Spawns the headless {@code server} fat JAR as a subprocess (VS BOT / LAN host).
 *
 * <p>Ready detection uses the server's stdout line {@value #LISTENING_LOG_MARKER}.
 * We intentionally do <em>not</em> open a TCP probe connection — that would consume
 * the only player slot in BOT mode and shut the server down when the probe closes.</p>
 */
public final class LocalServerProcess {

    /** Must match {@link io.github.some_example_name.server.GameServer} log output. */
    public static final String LISTENING_LOG_MARKER = "[GameServer] Listening on";

    private static final String JAR_ENV = "PINGPONG_SERVER_JAR";
    private static final int DEFAULT_TIMEOUT_MS = 8000;

    private Process process;
    private Thread logDrainer;

    /**
     * Starts the server and blocks until it logs that the listen socket is open.
     *
     * @return {@code true} if the server reported ready in time
     */
    public boolean start(MatchMode mode, String bindAddress, int port, int timeoutMs) {
        stop();
        Path jar = resolveServerJar();
        if (jar == null) {
            System.err.println("[LocalServerProcess] Server JAR not found. Set " + JAR_ENV);
            return false;
        }
        // Mode is decided per-JOIN by each connecting client, not at server
        // startup — the {@code mode} parameter is kept for API compatibility
        // but no longer forwarded as a CLI flag.
        AtomicBoolean ready = new AtomicBoolean(false);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                javaBinary(),
                "-jar", jar.toAbsolutePath().toString(),
                "--port", String.valueOf(port),
                "--bind", bindAddress
            );
            pb.directory(jar.getParent().toFile());
            pb.redirectErrorStream(true);
            process = pb.start();

            logDrainer = new Thread(() -> drainLogs(process, ready), "server-log-drain");
            logDrainer.setDaemon(true);
            logDrainer.start();

            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (System.nanoTime() < deadline) {
                if (ready.get()) {
                    return true;
                }
                if (!process.isAlive()) {
                    System.err.println("[LocalServerProcess] Server process exited early (code "
                        + process.exitValue() + ")");
                    return false;
                }
                Thread.sleep(50L);
            }
            System.err.println("[LocalServerProcess] Timed out waiting for server to listen");
            stop();
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else {
                System.err.println("[LocalServerProcess] Failed to start: " + e.getMessage());
            }
            stop();
            return false;
        }
    }

    public void stop() {
        if (logDrainer != null) {
            logDrainer.interrupt();
            logDrainer = null;
        }
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    private static void drainLogs(Process proc, AtomicBoolean ready) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(LISTENING_LOG_MARKER)) {
                    ready.set(true);
                }
            }
        } catch (IOException ignored) {
            // Process ended or stream closed.
        }
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        if (home != null) {
            Path bin = Paths.get(home, "bin", "java");
            if (Files.isExecutable(bin)) {
                return bin.toString();
            }
        }
        return "java";
    }

    private static Path resolveServerJar() {
        String env = System.getenv(JAR_ENV);
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }

        String version = System.getProperty("pingpong.version", "1.0.0");
        String appName = System.getProperty("pingpong.appName", "LibGDX-Versao3");
        String jarName = appName + "-server-" + version + ".jar";

        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
            cwd.resolve("server/build/libs").resolve(jarName),
            cwd.resolve("server/build/libs").resolve(appName + "-server-1.0.0.jar"),
            cwd.resolve(jarName),
            cwd.resolve("libs").resolve(jarName),
        };
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                return c;
            }
        }

        try {
            Path libsDir = cwd.resolve("server/build/libs");
            if (Files.isDirectory(libsDir)) {
                try (Stream<Path> stream = Files.list(libsDir)) {
                    return stream
                        .filter(p -> p.getFileName().toString().endsWith(".jar")
                            && p.getFileName().toString().contains("-server-"))
                        .findFirst()
                        .orElse(null);
                }
            }
        } catch (IOException ignored) {}

        return null;
    }

    public LocalServerProcess() {}
}
