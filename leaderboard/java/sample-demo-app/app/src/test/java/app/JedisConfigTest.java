/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;

/** Unit, timeout, pool-limit, TLS, Secret Manager, and concurrency tests for {@link JedisConfig}. */
class JedisConfigTest {

  private static JedisConfig newBaseConfig(final String secretValue) {
    JedisConfig config =
        new JedisConfig() {
          @Override
          String fetchSecretPayload(final String secretVersionResource) {
            return secretValue;
          }
        };
    ReflectionTestUtils.setField(config, "redisHost", "127.0.0.1");
    ReflectionTestUtils.setField(config, "redisPort", 6379);
    ReflectionTestUtils.setField(config, "redisUseTls", false);
    ReflectionTestUtils.setField(config, "connectTimeoutMs", 2000);
    ReflectionTestUtils.setField(config, "socketTimeoutMs", 2000);
    ReflectionTestUtils.setField(config, "poolMaxTotal", 16);
    ReflectionTestUtils.setField(config, "poolMaxIdle", 8);
    ReflectionTestUtils.setField(config, "poolMinIdle", 0);
    ReflectionTestUtils.setField(config, "poolMaxWaitMs", 2000L);
    ReflectionTestUtils.setField(config, "redisPasswordSecret", "");
    ReflectionTestUtils.setField(config, "redisPassword", "");
    ReflectionTestUtils.setField(config, "projectId", "");
    return config;
  }

  @Test
  @DisplayName("Rejects invalid host, port, timeout, or pool limit configuration")
  void rejectsInvalidConfiguration() {
    JedisConfig missingHost = newBaseConfig("");
    ReflectionTestUtils.setField(missingHost, "redisHost", " ");
    assertThrows(IllegalArgumentException.class, missingHost::jedis);

    JedisConfig invalidPort = newBaseConfig("");
    ReflectionTestUtils.setField(invalidPort, "redisPort", 70000);
    assertThrows(IllegalArgumentException.class, invalidPort::jedis);

    JedisConfig badConnectTimeout = newBaseConfig("");
    ReflectionTestUtils.setField(badConnectTimeout, "connectTimeoutMs", 0);
    assertThrows(IllegalArgumentException.class, badConnectTimeout::buildClientConfig);

    JedisConfig badSocketTimeout = newBaseConfig("");
    ReflectionTestUtils.setField(badSocketTimeout, "socketTimeoutMs", -1);
    assertThrows(IllegalArgumentException.class, badSocketTimeout::buildClientConfig);

    JedisConfig badMaxTotal = newBaseConfig("");
    ReflectionTestUtils.setField(badMaxTotal, "poolMaxTotal", 0);
    assertThrows(IllegalArgumentException.class, badMaxTotal::buildPoolConfig);

    JedisConfig badMaxIdle = newBaseConfig("");
    ReflectionTestUtils.setField(badMaxIdle, "poolMaxTotal", 8);
    ReflectionTestUtils.setField(badMaxIdle, "poolMaxIdle", 16);
    assertThrows(IllegalArgumentException.class, badMaxIdle::buildPoolConfig);

    JedisConfig badMinIdle = newBaseConfig("");
    ReflectionTestUtils.setField(badMinIdle, "poolMaxIdle", 4);
    ReflectionTestUtils.setField(badMinIdle, "poolMinIdle", 5);
    assertThrows(IllegalArgumentException.class, badMinIdle::buildPoolConfig);

    JedisConfig badPoolWait = newBaseConfig("");
    ReflectionTestUtils.setField(badPoolWait, "poolMaxWaitMs", 0L);
    assertThrows(IllegalArgumentException.class, badPoolWait::buildPoolConfig);
  }

  @Test
  @DisplayName("Configures TLS, timeouts, custom pool limits, Secret Manager, and env fallback")
  void configuresTlsTimeoutsPoolLimitsAndAuthPrecedence() {
    JedisConfig secretConfig = newBaseConfig("from-secret-manager");
    ReflectionTestUtils.setField(secretConfig, "redisUseTls", true);
    ReflectionTestUtils.setField(secretConfig, "connectTimeoutMs", 1500);
    ReflectionTestUtils.setField(secretConfig, "socketTimeoutMs", 2500);
    ReflectionTestUtils.setField(secretConfig, "poolMaxTotal", 32);
    ReflectionTestUtils.setField(secretConfig, "poolMaxIdle", 12);
    ReflectionTestUtils.setField(secretConfig, "poolMinIdle", 4);
    ReflectionTestUtils.setField(secretConfig, "poolMaxWaitMs", 1200L);
    ReflectionTestUtils.setField(secretConfig, "redisPasswordSecret", "valkey-password");
    ReflectionTestUtils.setField(secretConfig, "redisPassword", "local-env-password");
    ReflectionTestUtils.setField(secretConfig, "projectId", "demo-project");

    JedisClientConfig clientConfig = secretConfig.buildClientConfig();
    assertTrue(clientConfig.isSsl());
    assertEquals(1500, clientConfig.getConnectionTimeoutMillis());
    assertEquals(2500, clientConfig.getSocketTimeoutMillis());
    assertEquals("from-secret-manager", clientConfig.getPassword());

    ConnectionPoolConfig poolConfig = secretConfig.buildPoolConfig();
    assertEquals(32, poolConfig.getMaxTotal());
    assertEquals(12, poolConfig.getMaxIdle());
    assertEquals(4, poolConfig.getMinIdle());
    assertTrue(poolConfig.getBlockWhenExhausted());
    assertEquals(Duration.ofMillis(1200), poolConfig.getMaxWaitDuration());
    assertFalse(poolConfig.getJmxEnabled());

    JedisConfig localEnvConfig = new JedisConfig();
    ReflectionTestUtils.setField(localEnvConfig, "redisHost", "127.0.0.1");
    ReflectionTestUtils.setField(localEnvConfig, "redisPort", 6379);
    ReflectionTestUtils.setField(localEnvConfig, "redisUseTls", false);
    ReflectionTestUtils.setField(localEnvConfig, "connectTimeoutMs", 2000);
    ReflectionTestUtils.setField(localEnvConfig, "socketTimeoutMs", 2000);
    ReflectionTestUtils.setField(localEnvConfig, "redisPasswordSecret", "");
    ReflectionTestUtils.setField(localEnvConfig, "redisPassword", "local-dev-only");
    assertEquals("local-dev-only", localEnvConfig.buildClientConfig().getPassword());

    assertEquals(
        "projects/p1/secrets/s1/versions/3",
        JedisConfig.normalizeSecretVersionResource("projects/p1/secrets/s1/versions/3", ""));
    assertEquals(
        "projects/p1/secrets/s1/versions/latest",
        JedisConfig.normalizeSecretVersionResource("projects/p1/secrets/s1", ""));
    assertEquals(
        "projects/demo/secrets/valkey-pw/versions/latest",
        JedisConfig.normalizeSecretVersionResource("valkey-pw", "demo"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JedisConfig.normalizeSecretVersionResource("short-secret-id", ""));
  }

  @Test
  @DisplayName("Authenticates and isolates concurrent requests across pooled sockets")
  void isolatesConcurrentRequestsAcrossPooledConnections() throws Exception {
    try (MiniRespServer server = new MiniRespServer("s3cr3t", 10)) {
      JedisConfig config = new JedisConfig();
      ReflectionTestUtils.setField(config, "redisHost", "127.0.0.1");
      ReflectionTestUtils.setField(config, "redisPort", server.port());
      ReflectionTestUtils.setField(config, "redisUseTls", false);
      ReflectionTestUtils.setField(config, "connectTimeoutMs", 2000);
      ReflectionTestUtils.setField(config, "socketTimeoutMs", 2000);
      ReflectionTestUtils.setField(config, "poolMaxTotal", 16);
      ReflectionTestUtils.setField(config, "poolMaxIdle", 8);
      ReflectionTestUtils.setField(config, "poolMinIdle", 0);
      ReflectionTestUtils.setField(config, "poolMaxWaitMs", 2000L);
      ReflectionTestUtils.setField(config, "redisPasswordSecret", "");
      ReflectionTestUtils.setField(config, "redisPassword", "s3cr3t");

      int threadCount = 12;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      try (JedisPooled pooled = config.jedis()) {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
          final String key = "item-" + i;
          Callable<String> task =
              () -> {
                assertTrue(startGate.await(5, TimeUnit.SECONDS));
                return pooled.get(key);
              };
          futures.add(executor.submit(task));
        }
        startGate.countDown();
        for (int i = 0; i < threadCount; i++) {
          assertEquals("val:item-" + i, futures.get(i).get(5, TimeUnit.SECONDS));
        }
      } finally {
        executor.shutdownNow();
      }

      assertTrue(server.acceptedConnections() > 1, "Expected multiple pooled TCP connections");
      assertEquals(server.acceptedConnections(), server.authenticatedConnections());
    }
  }

  @Test
  @DisplayName("Enforces socket timeout when Valkey backend stalls")
  void enforcesSocketTimeoutOnStalledBackend() throws Exception {
    try (MiniRespServer stalledServer = new MiniRespServer("", 800)) {
      JedisConfig config = newBaseConfig("");
      ReflectionTestUtils.setField(config, "redisPort", stalledServer.port());
      ReflectionTestUtils.setField(config, "socketTimeoutMs", 120);

      try (JedisPooled pooled = config.jedis()) {
        assertThrows(JedisConnectionException.class, () -> pooled.get("slow-key"));
      }
    }
  }

  private static final class MiniRespServer implements AutoCloseable {
    private final String expectedPassword;
    private final long getDelayMs;
    private final ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger authenticated = new AtomicInteger();

    MiniRespServer(final String expectedPassword, final long getDelayMs) throws IOException {
      this.expectedPassword = expectedPassword;
      this.getDelayMs = getDelayMs;
      this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      clientPool.execute(this::acceptLoop);
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    int acceptedConnections() {
      return accepted.get();
    }

    int authenticatedConnections() {
      return authenticated.get();
    }

    private void acceptLoop() {
      while (!serverSocket.isClosed()) {
        try {
          Socket socket = serverSocket.accept();
          accepted.incrementAndGet();
          clientPool.execute(() -> handleClient(socket));
        } catch (IOException ignored) {
          return;
        }
      }
    }

    private void handleClient(final Socket socket) {
      try (Socket s = socket;
          InputStream in = s.getInputStream();
          OutputStream out = s.getOutputStream()) {
        List<String> cmd;
        while ((cmd = readRespArray(in)) != null && !cmd.isEmpty()) {
          String op = cmd.get(0).toUpperCase();
          if ("AUTH".equals(op)) {
            String supplied = cmd.get(cmd.size() - 1);
            if (expectedPassword.equals(supplied)) {
              authenticated.incrementAndGet();
              out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
              out.write("-ERR invalid password\r\n".getBytes(StandardCharsets.UTF_8));
            }
          } else if ("PING".equals(op)) {
            out.write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
          } else if ("GET".equals(op)) {
            Thread.sleep(getDelayMs);
            String payload = "val:" + cmd.get(1);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            String header = "$" + bytes.length + "\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
          } else {
            out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
          }
          out.flush();
        }
      } catch (IOException | InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }

    private static List<String> readRespArray(final InputStream in) throws IOException {
      String header = readLine(in);
      if (header == null || header.isEmpty() || header.charAt(0) != '*') {
        return null;
      }
      int count = Integer.parseInt(header.substring(1));
      List<String> parts = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        String bulkLenLine = readLine(in);
        if (bulkLenLine == null || bulkLenLine.charAt(0) != '$') {
          return null;
        }
        int len = Integer.parseInt(bulkLenLine.substring(1));
        byte[] buf = in.readNBytes(len);
        in.readNBytes(2);
        parts.add(new String(buf, StandardCharsets.UTF_8));
      }
      return parts;
    }

    private static String readLine(final InputStream in) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      int prev = -1;
      int curr;
      while ((curr = in.read()) != -1) {
        if (prev == '\r' && curr == '\n') {
          byte[] bytes = bos.toByteArray();
          return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
        }
        bos.write(curr);
        prev = curr;
      }
      return null;
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      clientPool.shutdownNow();
    }
  }
}
