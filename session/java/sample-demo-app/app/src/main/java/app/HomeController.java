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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Controller
public class HomeController {

  @GetMapping("/")
  public String home(Model model) {
    return "index"; // Refers to templates/index.html
  }

  @GetMapping("/login")
  public String login(Model model) {
    return "login"; // Refers to templates/login.html
  }

  @GetMapping("/register")
  public String logout(Model model) {
    return "register"; // Refers to templates/register.html
  }

  @GetMapping("/images/{id}")
  @ResponseBody
  public ResponseEntity<byte[]> proxyImage(@PathVariable String id) throws IOException {
      String imageUrl = "https://picsum.photos/id/" + id + "/300/200";
      HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
      conn.setRequestProperty("User-Agent", "Mozilla/5.0");

      try (InputStream in = conn.getInputStream()) {
          byte[] imageBytes = in.readAllBytes();
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.IMAGE_JPEG);
          return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
      }
  }
}
