/*
 * Copyright 2025 Google LLC
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

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Protocol;

/** Configuration for a thread-safe pooled Jedis client connected to the Valkey server. */
@Configuration
public class JedisConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(JedisConfig.class);
  private static final int MAX_PORT = 65535;

  @Value("${VALKEY_HOST:localhost}")
  private String redisHost;

  @Value("${VALKEY_PORT:6379}")
  private int redisPort;

  @Value("${VALKEY_USE_TLS:${VALKEY_SSL:false}}")
  private boolean redisUseTls;

  @Value("${VALKEY_CONNECT_TIMEOUT_MS:2000}")
  private int connectTimeoutMs;

  @Value("${VALKEY_SOCKET_TIMEOUT_MS:2000}")
  private int socketTimeoutMs;

  @Value("${VALKEY_POOL_MAX_TOTAL:16}")
  private int poolMaxTotal;

  @Value("${VALKEY_POOL_MAX_IDLE:8}")
  private int poolMaxIdle;

  @Value("${VALKEY_POOL_MIN_IDLE:0}")
  private int poolMinIdle;

  @Value("${VALKEY_POOL_MAX_WAIT_MS:2000}")
  private long poolMaxWaitMs;

  @Value("${VALKEY_PASSWORD_SECRET:}")
  private String redisPasswordSecret;

  @Value("${VALKEY_PASSWORD:}")
  private String redisPassword;

  @Value("${GOOGLE_CLOUD_PROJECT:}")
  private String projectId;

  /**
   * Creates and verifies a thread-safe {@link JedisPooled} client instance.
   *
   * @return configured {@link JedisPooled} client
   */
  @Bean(destroyMethod = "close")
  public JedisPooled jedis() {
    if (redisHost == null || redisHost.isBlank()) {
      throw new IllegalArgumentException("Redis host (VALKEY_HOST) is not configured");
    }
    if (redisPort <= 0 || redisPort > MAX_PORT) {
      throw new IllegalArgumentException("Redis port (VALKEY_PORT) is invalid");
    }

    JedisClientConfig clientConfig = buildClientConfig();
    ConnectionPoolConfig poolConfig = buildPoolConfig();

    JedisPooled jedis =
        new JedisPooled(new HostAndPort(redisHost.trim(), redisPort), clientConfig, poolConfig);

    try {
      jedis.sendCommand(Protocol.Command.PING);
    } catch (Exception e) {
      jedis.close();
      throw new RuntimeException(
          "Failed to connect to Redis server at " + redisHost + ":" + redisPort, e);
    }

    return jedis;
  }

  JedisClientConfig buildClientConfig() {
    if (connectTimeoutMs <= 0) {
      throw new IllegalArgumentException("VALKEY_CONNECT_TIMEOUT_MS must be positive");
    }
    if (socketTimeoutMs <= 0) {
      throw new IllegalArgumentException("VALKEY_SOCKET_TIMEOUT_MS must be positive");
    }

    DefaultJedisClientConfig.Builder builder =
        DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(connectTimeoutMs)
            .socketTimeoutMillis(socketTimeoutMs)
            .ssl(redisUseTls);

    String resolvedPassword = resolvePassword();
    if (resolvedPassword != null && !resolvedPassword.isEmpty()) {
      builder.password(resolvedPassword);
    }

    return builder.build();
  }

  private String resolvePassword() {
    if (redisPasswordSecret != null && !redisPasswordSecret.isBlank()) {
      String versionResource = normalizeSecretVersionResource(redisPasswordSecret, projectId);
      return fetchSecretPayload(versionResource);
    }
    if (redisPassword != null && !redisPassword.isEmpty()) {
      LOGGER.warn(
          "Using VALKEY_PASSWORD from environment; prefer VALKEY_PASSWORD_SECRET "
              + "(Secret Manager) outside local development.");
      return redisPassword;
    }
    return "";
  }

  ConnectionPoolConfig buildPoolConfig() {
    if (poolMaxTotal <= 0) {
      throw new IllegalArgumentException("VALKEY_POOL_MAX_TOTAL must be positive");
    }
    if (poolMaxIdle < 0 || poolMaxIdle > poolMaxTotal) {
      throw new IllegalArgumentException(
          "VALKEY_POOL_MAX_IDLE must be between 0 and VALKEY_POOL_MAX_TOTAL");
    }
    if (poolMinIdle < 0 || poolMinIdle > poolMaxIdle) {
      throw new IllegalArgumentException(
          "VALKEY_POOL_MIN_IDLE must be between 0 and VALKEY_POOL_MAX_IDLE");
    }
    if (poolMaxWaitMs <= 0) {
      throw new IllegalArgumentException("VALKEY_POOL_MAX_WAIT_MS must be positive");
    }

    ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
    poolConfig.setMaxTotal(poolMaxTotal);
    poolConfig.setMaxIdle(poolMaxIdle);
    poolConfig.setMinIdle(poolMinIdle);
    poolConfig.setBlockWhenExhausted(true);
    poolConfig.setMaxWait(Duration.ofMillis(poolMaxWaitMs));
    poolConfig.setJmxEnabled(false);
    return poolConfig;
  }

  static String normalizeSecretVersionResource(
      final String secretRef, final String defaultProjectId) {
    String trimmed = secretRef.trim();
    if (trimmed.startsWith("projects/")) {
      if (trimmed.contains("/versions/")) {
        return trimmed;
      }
      return trimmed + "/versions/latest";
    }
    if (defaultProjectId == null || defaultProjectId.isBlank()) {
      throw new IllegalArgumentException(
          "VALKEY_PASSWORD_SECRET must be a full projects/*/secrets/*/versions/* resource name "
              + "or GOOGLE_CLOUD_PROJECT must be set");
    }
    return SecretVersionName.of(defaultProjectId.trim(), trimmed, "latest").toString();
  }

  String fetchSecretPayload(final String secretVersionResource) {
    try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      return client.accessSecretVersion(secretVersionResource).getPayload().getData().toStringUtf8();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to initialize Secret Manager client for VALKEY_PASSWORD_SECRET", e);
    }
  }
}
