# mackerel-to-java-otel-apm-example

Mackerel APM に Java の OpenTelemetry トレースを送るためのデモです。

- `spring-app`: OpenTelemetry Java Agent で自動計装する Spring Boot アプリ
- `manual-app`: OpenTelemetry SDK を使って手動計装する Java アプリ
- `otel-collector`: OTLP を受信して Mackerel に転送
- `postgres`: デモ用 DB

## 前提条件

- Docker / Docker Compose が使えること
- Mackerel の API キー（APM 送信用）

## セットアップ

1. リポジトリを取得します。

```bash
git clone git@github.com:soudai/mackerel-to-java-otel-apm-example.git
cd mackerel-to-java-otel-apm-example
```

2. 環境変数ファイルを作成します。

```bash
cp .env.example .env
```

3. `.env` の `MACKEREL_APIKEY` に API キーを設定します。

```dotenv
MACKEREL_APIKEY="<YOUR_MACKEREL_API_KEY>"
```

## デモの起動

以下でビルドと起動を行います。

```bash
docker compose up --build -d
```

起動確認:

```bash
docker compose ps
curl http://localhost:8080/health
curl http://localhost:8081/health
```

## デモの実行

### 自動計装（spring-app）

`spring-app` は OpenTelemetry Java Agent によって自動計装されます。

- `GET /health` (`http://localhost:8080/health`)
  - ヘルスチェック。`ok` を返します。
- `GET /products` (`http://localhost:8080/products`)
  - PostgreSQL の `products` テーブルを参照して JSON を返します。
- `GET /manual/hello` (`http://localhost:8080/manual/hello`)
  - `manual-app` の `/hello` を呼び出し、結果を返します（サービス間トレース伝播を確認可能）。
- `GET /manual/db` (`http://localhost:8080/manual/db`)
  - `manual-app` の `/db` を呼び出し、DB クエリを含む処理結果を返します。

```bash
curl http://localhost:8080/health
curl http://localhost:8080/products
curl http://localhost:8080/manual/hello
curl http://localhost:8080/manual/db
```

### 手動計装（manual-app）

`manual-app` は OpenTelemetry SDK で明示的に Span を作成しています。

- `GET /health` (`http://localhost:8081/health`)
  - ヘルスチェック。`ok` を返します。
- `GET /hello` (`http://localhost:8081/hello`)
  - サーバー Span (`GET /hello`) と子 Span (`app.work`) を生成します。
- `GET /db` (`http://localhost:8081/db`)
  - サーバー Span (`GET /db`) と DB 用子 Span (`db.query products`) を生成し、DB 結果を返します。
- `GET /error` (`http://localhost:8081/error`)
  - 例外を発生させ、エラー Span を送信します。

```bash
curl http://localhost:8081/health
curl http://localhost:8081/hello
curl http://localhost:8081/db
curl http://localhost:8081/error
```

トレースを増やしたい場合:

```bash
for i in {1..20}; do curl -s http://localhost:8080/manual/db >/dev/null; done
```

## Mackerel での確認

しばらく待ってから Mackerel APM で、以下のサービス名のトレースが届いていることを確認します。

- `java-spring-agent`
- `java-manual-sdk`

## ログ確認と停止

Collector ログ確認:

```bash
docker compose logs -f otel-collector
```

停止:

```bash
docker compose down
```

データも削除する場合:

```bash
docker compose down -v
```
