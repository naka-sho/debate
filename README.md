# AI Debate

2つのAIがリアルタイムでディベートを行うWebアプリケーション。

## 概要

指定したテーマに対して、賛成側・反対側に分かれた2つのAIが交互に発言し、ディベートの様子をリアルタイムでストリーミング表示します。

**デフォルトの対戦カード:**
- AI1: Qwen 2.5（賛成側）
- AI2: Llama 3.1（反対側）

## 技術スタック

| カテゴリ | 技術 |
|---|---|
| バックエンド | Spring Boot 4.0.3 / Java 25 |
| AI | Spring AI 2.0.0-M2 (Ollama) |
| フロントエンド | Thymeleaf / HTMX / Tailwind CSS |
| データベース | H2 (ファイルモード) |
| リアルタイム通信 | Server-Sent Events (SSE) |

## セットアップ

### 前提条件

- Java 25+
- Docker / Docker Compose

### 1. Ollamaコンテナを起動

```bash
docker compose up -d
```

### 2. モデルをプル

```bash
docker exec debate-ollama-qwen ollama pull qwen2.5:7b
docker exec debate-ollama-llama ollama pull llama3.1:8b
```

### 3. アプリを起動

```bash
./gradlew bootRun
```

ブラウザで http://localhost:8080 を開く。

## 使い方

1. トップページでディベートテーマを選択（またはカスタム入力）
2. 「ディベート開始」ボタンをクリック
3. AIの発言がリアルタイムでストリーミング表示される
4. デフォルトで60分間ディベートが継続する

## 設定

`src/main/resources/application.yaml` で変更可能:

| 設定キー | デフォルト | 説明 |
|---|---|---|
| `debate.duration-minutes` | `60` | ディベートの実施時間（分） |
| `debate.max-tokens` | `400` | 1発言あたりの最大トークン数 |
| `debate.temperature` | `0.7` | 生成温度 |
| `debate.context-window` | `10` | 過去発言の参照件数 |
| `debate.mode` | `external-api` | `ollama` または `external-api` |

### 動作モード

- **`ollama`**: ローカルのOllamaコンテナに直接接続
- **`external-api`**: 外部APIサーバー（`debate.external-api.base-url`）の `/ask` エンドポイントを使用

## SSEイベント一覧

| イベント名 | ペイロード | 説明 |
|---|---|---|
| `debate-start` | `{sessionId, topic, ai1Name, ai2Name}` | ディベート開始 |
| `message-start` | `{tempId, speaker, aiName, aiColor, roundNumber}` | 発言開始 |
| `token` | `{tempId, text}` | トークンのストリーミング |
| `message-complete` | `{tempId, messageId, content}` | 発言完了 |
| `status` | `{remainingSeconds, roundCount}` | 進行状況 |
| `debate-complete` | `{reason}` | ディベート終了 |

## ディベートテーマ（プリセット）

- AIは人類の仕事を奪うか
- 人類はテクノロジーに依存しすぎているか
- 原子力発電を廃止すべきか
- 週4日労働制を導入すべきか
- 宇宙開発への投資は正当化されるか
- SNSは民主主義にとって有害か
- ベーシックインカムを導入すべきか

## エンドポイント

| メソッド | パス | 説明 |
|---|---|---|
| GET | `/` | トップページ（進行中or最新セッション表示） |
| POST | `/debate/start` | ディベート開始 |
| GET | `/debate/events` | SSEストリーム |
| GET | `/debate/history` | セッション一覧 |
| GET | `/debate/history/{id}` | セッション詳細 |
| GET | `/h2-console` | H2データベースコンソール |
