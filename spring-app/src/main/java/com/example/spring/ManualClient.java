package com.example.spring;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ManualClient {
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2))
      .build();

  private final String baseUrl = System.getenv().getOrDefault("MANUAL_APP_BASE_URL", "http://manual-app:8081");

  public String hello() {
    return get(baseUrl + "/hello");
  }

  public String db() {
    return get(baseUrl + "/db");
  }

  private String get(String url) {
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
      return "status=" + res.statusCode() + "\n" + res.body();
    } catch (Exception e) {
      return "error: " + e.getMessage();
    }
  }
}
