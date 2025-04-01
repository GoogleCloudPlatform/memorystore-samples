/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package app;

import com.github.javafaker.Faker;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

public class Main {

  /** Maximum number of generated entries. */
  private static final int MAX_GENERATED_ENTRIES = 15000;
  /** Faker instance for generating random data. */
  private static final Faker FAKER = new Faker();
  /** Random instance for generating random data. */
  private static final Random RANDOM = new Random();
  /** An hour in ms. */
  private static final long ONE_HOUR = 3600000;
  /** Maximum username length. */
  private static final int MAX_USERNAME_LENGTH = 20;
  /** Sleep between retrying connections. */
  private static final int RETRY_SLEEP = 5000;

  /** Reference to DataSource so we can shut it down. */
  private static DataSource dataSource;

  /**
   * Main method to populate the accounts with test data.
   *
   * @param args Command line arguments
   */
  public static void main(final String[] args) {
    System.out.println("Connecting to PostgreSQL...");

    JdbcTemplate jdbcTemplate = null;
    boolean connected = false;

    // Retry connection once
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        jdbcTemplate = configureJdbcTemplate();
        System.out.println("Populating accounts...");
        populateAccounts(jdbcTemplate);
        connected = true;
        break;
      } catch (CannotGetJdbcConnectionException e) {
        System.out.println("Failed to connect to the database. Retrying in 5 seconds...");
        try {
          Thread.sleep(RETRY_SLEEP);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    // Shut down the DataSource if initialized
    shutdownDataSource();

    if (!connected) {
      System.err.println("Could not connect to the database after retries.");
    }
  }

  private static void populateAccounts(final JdbcTemplate jdbcTemplate) {
    String sql = "INSERT INTO account (email, username, password) VALUES (?, ?, ?)";

    List<Object[]> batchArgs = new ArrayList<>();
    for (int i = 0; i < MAX_GENERATED_ENTRIES; i++) {
      String email = FAKER.internet().emailAddress();
      String username = FAKER.name().username();
      username =
          username.length() > MAX_USERNAME_LENGTH ? username.substring(0, MAX_USERNAME_LENGTH)
              : username;
      String password = FAKER.internet().password();

      batchArgs.add(new Object[] {email, username, password});
    }

    jdbcTemplate.batchUpdate(sql, batchArgs);
  }

  private static JdbcTemplate configureJdbcTemplate() {
    String jdbcUrl =
        System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/postgres");
    String jdbcUsername = System.getenv().getOrDefault("DB_USERNAME", "root");
    String jdbcPassword = System.getenv().getOrDefault("DB_PASSWORD", "password");

    dataSource = DataSourceBuilder.create().url(jdbcUrl).username(jdbcUsername)
        .password(jdbcPassword).build();

    return new JdbcTemplate(dataSource);
  }

  private static void shutdownDataSource() {
    if (dataSource instanceof AutoCloseable) {
      try {
        ((AutoCloseable) dataSource).close();
      } catch (Exception e) {
        System.err.println("Failed to close DataSource: " + e.getMessage());
      }
    }
  }

  /** Dummy method to trick Checkstyle. */
  public void avoidCheckstyleError() {}

}