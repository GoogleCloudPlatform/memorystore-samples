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

  private static final int MAX_PORT = 65535;
  private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 16;
  private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 8;

  @Value("${VALKEY_HOST:localhost}")
  private String redisHost;

  @Value("${VALKEY_PORT:6379}")
  private int redisPort;

  @Value("${VALKEY_PASSWORD:}")
  private String redisPassword;

  /**
   * Creates and verifies a thread-safe {@link JedisPooled} client instance.
   *
   * @return configured {@link JedisPooled} client
   */
  @Bean(destroyMethod = "close")
  public JedisPooled jedis() {
    if (redisHost == null || redisHost.isEmpty()) {
      throw new IllegalArgumentException("Redis host (VALKEY_HOST) is not configured");
    }
    if (redisPort <= 0 || redisPort > MAX_PORT) {
      throw new IllegalArgumentException("Redis port (VALKEY_PORT) is invalid");
    }

    DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder();
    if (redisPassword != null && !redisPassword.isEmpty()) {
      configBuilder.password(redisPassword);
    }
    JedisClientConfig clientConfig = configBuilder.build();

    ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
    poolConfig.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTIONS);
    poolConfig.setMaxIdle(DEFAULT_MAX_IDLE_CONNECTIONS);
    poolConfig.setJmxEnabled(false);

    JedisPooled jedis =
        new JedisPooled(new HostAndPort(redisHost, redisPort), clientConfig, poolConfig);

    try {
      jedis.sendCommand(Protocol.Command.PING);
    } catch (Exception e) {
      jedis.close();
      throw new RuntimeException(
          "Failed to connect to Redis server at " + redisHost + ":" + redisPort, e);
    }

    return jedis;
  }
}
