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

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures Cross-Origin Resource Sharing (CORS) settings for the application. */
@Configuration
public class WebConfig {

  /** Allowed origin URLs for CORS (comma-separated). Defaults to local development origin. */
  @Value("${ALLOWED_ORIGINS:http://localhost:8080}")
  private String allowedOrigins;

  /** Allowed HTTP methods for CORS (comma-separated). */
  @Value("${ALLOWED_METHODS:GET,POST,PUT,DELETE}")
  private String allowedMethods;

  /** Allowed HTTP headers for CORS requests (comma-separated). */
  @Value("${ALLOWED_HEADERS:*}")
  private String allowedHeaders;

  /**
   * Registers CORS mappings with explicit allowed origins.
   *
   * @return {@link WebMvcConfigurer} with restricted CORS configuration
   */
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(final CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOrigins(parseCsv(allowedOrigins))
            .allowedMethods(parseCsv(allowedMethods))
            .allowedHeaders(parseCsv(allowedHeaders))
            .allowCredentials(true);
      }
    };
  }

  private static String[] parseCsv(final String csv) {
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .toArray(String[]::new);
  }
}
