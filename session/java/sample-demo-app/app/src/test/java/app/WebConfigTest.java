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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Unit tests verifying {@link WebConfig} restricts credentialed CORS requests. */
@WebMvcTest(HomeController.class)
@Import(WebConfig.class)
class WebConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Allows default http://localhost:8080 origin with credentials")
  void allowsDefaultLocalhostOrigin() throws Exception {
    mockMvc
        .perform(
            options("/")
                .header(HttpHeaders.ORIGIN, "http://localhost:8080")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8080"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  @Test
  @DisplayName("Rejects untrusted external origin on preflight and credentialed requests")
  void rejectsUntrustedExternalOrigin() throws Exception {
    mockMvc
        .perform(
            options("/")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

    mockMvc
        .perform(get("/").header(HttpHeaders.ORIGIN, "https://evil.example"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Nested
  @TestPropertySource(
      properties = "ALLOWED_ORIGINS=https://app.example.com, https://admin.example.com")
  class CustomAllowedOriginsTest {

    @Autowired private MockMvc customMockMvc;

    @Test
    @DisplayName("Allows trimmed comma-separated configured origins and blocks unlisted origins")
    void honorsConfiguredAllowedOrigins() throws Exception {
      customMockMvc
          .perform(
              options("/")
                  .header(HttpHeaders.ORIGIN, "https://admin.example.com")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
          .andExpect(status().isOk())
          .andExpect(
              header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example.com"))
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

      customMockMvc
          .perform(
              options("/")
                  .header(HttpHeaders.ORIGIN, "https://evil.example")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
  }

  @Nested
  @TestPropertySource(properties = "ALLOWED_ORIGINS=*")
  class WildcardOriginGuardTest {

    @Autowired private MockMvc wildcardMockMvc;

    @Test
    @DisplayName("Refuses wildcard ALLOWED_ORIGINS when allowCredentials is enabled")
    void refusesWildcardWithCredentials() {
      Exception thrown =
          assertThrows(
              Exception.class,
              () ->
                  wildcardMockMvc.perform(
                      options("/")
                          .header(HttpHeaders.ORIGIN, "https://evil.example")
                          .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")));
      boolean hasIllegalArgument =
          thrown instanceof IllegalArgumentException
              || thrown.getCause() instanceof IllegalArgumentException;
      assertTrue(hasIllegalArgument, "Expected IllegalArgumentException for wildcard origin");
    }
  }
}
