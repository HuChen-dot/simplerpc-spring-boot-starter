package org.hu.rpcintegration;

import org.junit.Assume;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 每组测试使用独立端口和临时目录，绝不连接用户的 Redis。 */
final class RedisTestServer implements AutoCloseable {
    private final String binary = System.getProperty("redis.test.binary", "redis-server");
    private final int port;
    private final Path directory;
    private Process process;

    RedisTestServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        directory = Files.createTempDirectory("simplerpc-redis-test-");
        start();
    }

    String address() { return "redis://:registry-test@127.0.0.1:" + port + "/0"; }

    Jedis client() {
        Jedis client = new Jedis("127.0.0.1", port, 1000);
        client.auth("registry-test");
        return client;
    }

    private void start() throws Exception {
        try {
            process = new ProcessBuilder(binary, "--bind", "127.0.0.1", "--port", String.valueOf(port),
                    "--save", "", "--appendonly", "no", "--protected-mode", "yes", "--requirepass", "registry-test",
                    "--dir", directory.toString()).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("redis.log").toFile()).start();
        } catch (IOException e) {
            if (System.getProperty("redis.test.binary") != null) { throw e; }
            Assume.assumeNoException("Redis integration tests require redis-server or -Dredis.test.binary=/path/to/redis-server", e);
        }
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            try (Jedis client = client()) { client.ping(); return; }
            catch (RuntimeException e) { Thread.sleep(20); }
        }
        close();
        throw new IllegalStateException("Test Redis did not start; log: " + directory.resolve("redis.log"));
    }

    void restart() throws Exception { close(); start(); }

    @Override
    public void close() throws Exception {
        if (process != null) {
            process.destroy();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
        }
    }
}
