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
import redis.clients.jedis.JedisPooled;

/** Unit and concurrency tests for {@link JedisConfig}. */
class JedisConfigTest {

  @Test
  @DisplayName("Rejects missing host or out-of-range port")
  void rejectsInvalidConfiguration() {
    JedisConfig missingHost = new JedisConfig();
    ReflectionTestUtils.setField(missingHost, "redisHost", "");
    ReflectionTestUtils.setField(missingHost, "redisPort", 6379);
    ReflectionTestUtils.setField(missingHost, "redisPassword", "");
    assertThrows(IllegalArgumentException.class, missingHost::jedis);

    JedisConfig invalidPort = new JedisConfig();
    ReflectionTestUtils.setField(invalidPort, "redisHost", "127.0.0.1");
    ReflectionTestUtils.setField(invalidPort, "redisPort", 70000);
    ReflectionTestUtils.setField(invalidPort, "redisPassword", "");
    assertThrows(IllegalArgumentException.class, invalidPort::jedis);
  }

  @Test
  @DisplayName("Authenticates pooled connections and isolates concurrent requests across sockets")
  void isolatesConcurrentRequestsAcrossPooledConnections() throws Exception {
    try (MiniRespServer server = new MiniRespServer("s3cr3t")) {
      JedisConfig config = new JedisConfig();
      ReflectionTestUtils.setField(config, "redisHost", "127.0.0.1");
      ReflectionTestUtils.setField(config, "redisPort", server.port());
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

  private static final class MiniRespServer implements AutoCloseable {
    private final String expectedPassword;
    private final ServerSocket serverSocket;
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger authenticated = new AtomicInteger();

    MiniRespServer(final String expectedPassword) throws IOException {
      this.expectedPassword = expectedPassword;
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
            Thread.sleep(10);
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
