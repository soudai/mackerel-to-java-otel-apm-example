package com.example.manual;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

public class ManualOtelServer {

  // W3C trace context のヘッダー（traceparent 等）をHttpServerで読めるようにする
  private static final TextMapGetter<HttpExchange> GETTER = new TextMapGetter<>() {
    @Override
    public Iterable<String> keys(HttpExchange carrier) {
      return carrier.getRequestHeaders().keySet();
    }

    @Override
    public String get(HttpExchange carrier, String key) {
      if (carrier == null || key == null) return null;
      // ヘッダーは複数値の可能性があるが、ここでは先頭だけ使う
      List<String> values = carrier.getRequestHeaders().get(key);
      return (values == null || values.isEmpty()) ? null : values.get(0);
    }
  };

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(Optional.ofNullable(System.getenv("PORT")).orElse("8081"));

    String jdbcUrl = Optional.ofNullable(System.getenv("JDBC_URL"))
        .orElse("jdbc:postgresql://postgres:5432/appdb");
    String dbUser = Optional.ofNullable(System.getenv("DB_USER")).orElse("app");
    String dbPassword = Optional.ofNullable(System.getenv("DB_PASSWORD")).orElse("secret");

    // env変数（OTEL_*）を読んでSDKを初期化
    AutoConfiguredOpenTelemetrySdk auto = AutoConfiguredOpenTelemetrySdk.initialize();
    OpenTelemetry otel = auto.getOpenTelemetrySdk();
    Tracer tracer = otel.getTracer("manual-app");

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        auto.getOpenTelemetrySdk().getSdkTracerProvider().shutdown();
      } catch (Throwable ignored) {}
    }));

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(Executors.newFixedThreadPool(8));

    server.createContext("/health", ex -> respond(ex, 200, "ok\n"));

    server.createContext("/hello", ex -> {
      Context extracted = otel.getPropagators().getTextMapPropagator()
          .extract(Context.current(), ex, GETTER);

      Span span = tracer.spanBuilder("GET /hello")
          .setParent(extracted)
          .setSpanKind(SpanKind.SERVER)
          .startSpan();

      try (Scope scope = span.makeCurrent()) {
        span.setAttribute("http.method", ex.getRequestMethod());
        span.setAttribute("url.path", ex.getRequestURI().getPath());

        // 手動で子スパンを切る（例: ちょっとした処理）
        Span child = tracer.spanBuilder("app.work").startSpan();
        try (Scope childScope = child.makeCurrent()) {
          Thread.sleep(30);
        } finally {
          child.end();
        }

        respond(ex, 200, "hello from manual-app\n");
      } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        respond(ex, 500, "error\n");
      } finally {
        span.end();
      }
    });

    server.createContext("/db", ex -> {
      Context extracted = otel.getPropagators().getTextMapPropagator()
          .extract(Context.current(), ex, GETTER);

      Span span = tracer.spanBuilder("GET /db")
          .setParent(extracted)
          .setSpanKind(SpanKind.SERVER)
          .startSpan();

      try (Scope scope = span.makeCurrent()) {
        span.setAttribute("http.method", ex.getRequestMethod());
        span.setAttribute("url.path", ex.getRequestURI().getPath());

        // DBクエリを手動でSpan化
        Span dbSpan = tracer.spanBuilder("db.query products")
            .setSpanKind(SpanKind.CLIENT)
            .startSpan();

        StringBuilder body = new StringBuilder();
        try (Scope dbScope = dbSpan.makeCurrent()) {
          dbSpan.setAttribute("db.system", "postgresql");
          dbSpan.setAttribute("db.operation", "SELECT");

          try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
               PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM products ORDER BY id LIMIT 50");
               ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              body.append(rs.getInt("id")).append(": ").append(rs.getString("name")).append("\n");
            }
          }
        } catch (Exception e) {
          dbSpan.recordException(e);
          dbSpan.setStatus(StatusCode.ERROR);
          throw e;
        } finally {
          dbSpan.end();
        }

        respond(ex, 200, body.toString());
      } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        respond(ex, 500, "db error\n");
      } finally {
        span.end();
      }
    });

    server.createContext("/error", ex -> {
      Span span = tracer.spanBuilder("GET /error").setSpanKind(SpanKind.SERVER).startSpan();
      try (Scope scope = span.makeCurrent()) {
        throw new RuntimeException("boom!");
      } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        respond(ex, 500, "boom\n");
      } finally {
        span.end();
      }
    });

    server.start();
    System.out.println("manual-app listening on 0.0.0.0:" + port);
  }

  private static void respond(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }
}
