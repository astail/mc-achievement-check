# AchievementCheck

**バニラ実績（advancement）の「あと何が足りないか」を、各プレイヤーが自分で確認できる**プラグインです（Paper 用 / サーバー側のみ）。

「全バイオーム制覇（Adventuring Time）まであと少し、でも**どのバイオームに行っていないのか分からない**」をなくします。`/ac` でメニューを開き、実績アイコンにカーソルを合わせると、**未訪問のバイオームや未達成の条件が具体名で**表示されます。アイコンをクリックすれば全条件をページ送りで確認できます。

> **クライアント MOD は不要です。** サーバーにこのプラグインを入れるだけで、バニラのクライアントでもそのまま動作します。

## 解決する課題

「Adventuring Time（全バイオーム）」「A Balanced Diet（全食料）」「Monsters Hunted（全モブ討伐）」のような**複数条件のバニラ実績**は、達成状況がサーバーに保存されていますが、ゲーム内の実績画面では**残りの具体的な項目が分かりません**。
AchievementCheck は、各プレイヤーの実績進捗を読み取り、**達成済み / 未達成の条件を 1 件ずつ**表示します。「次にどこへ行けば／何を食べれば／何を倒せばいいか」がひと目で分かります。

## 主な機能

- **GUI メニュー（主軸）**: `/ac` でチェスト型メニューを開き、コレクション系実績を一覧表示。アイコンにカーソルを合わせると、進捗（`達成 n / m`）と**未達成の条件**が説明（ツールチップ）に出ます。
- **詳細画面**: 一覧でアイコンをクリックすると、その実績の**全条件をページ送り**で表示。未達成を先頭に、達成済みは緑＋光沢で区別します。
- **チャット表示（補助）**: `/ac <実績名>` で、未達成・達成済みの条件をチャットに一覧表示します。
- **自動ローカライズ**: 実績タイトルやバイオーム / アイテム / モブ名は translatable 表示なので、**各プレイヤーのクライアント言語**（日本語など）で表示されます。翻訳できないときは整形したキー名にフォールバックします。
- **OR 実績は自動除外**: 「どれか 1 つ満たせば完了」する実績は「不足」の概念が合わないため、一覧から除外します。

## 動作要件

- サーバー: Paper 26.2（experimental チャンネル）
- Java: 25
- クライアント: バニラで可（MOD 不要・サーバー側のみ）

## 導入

1. `AchievementCheck-1.0.0.jar` を `plugins/` に置いてサーバーを再起動します。
2. 以降、各プレイヤーが `/ac` で自分の実績進捗を確認できます。設定は不要です。

## 使い方

導入するだけで `/ac` が使えます。挙動を変えたいときは `config.yml`（後述）か、`/ac reload` で調整します。

- メニューを開いて不足項目を見たい → `/ac`
- 特定の実績をチャットで確認したい → `/ac <実績名>`（例: `/ac adventuring_time`。タブ補完できます）
- 設定（`config.yml`）を変更して反映したい → `/ac reload`

GUI の操作:

- 実績アイコンに**カーソルを合わせる** → 進捗と未達成の条件が説明に出ます。
- 実績アイコンを**クリック** → その実績の全条件（ページ送り）を表示します。
- 下段の矢印で**ページ送り**、`一覧へ戻る` で一覧へ、`閉じる` で終了します。

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/ac` | 実績確認メニュー（GUI）を開く | `achievementcheck.use` |
| `/ac <実績名>` | 指定実績の不足条件・達成条件をチャットで表示 | `achievementcheck.use` |
| `/ac reload` | 設定を再読み込み | `achievementcheck.manage` |

エイリアス: `/achievementcheck`

## 権限

| 権限ノード | 説明 | 既定 |
|---|---|---|
| `achievementcheck.use` | `/ac` の自分向け操作（GUI・詳細表示） | `true`（全員） |
| `achievementcheck.manage` | `reload` などサーバー全体に影響する操作 | `op` |

## 設定（`config.yml`）

```yaml
enabled: true            # プラグインを有効にするか（false の間は /ac の確認機能を無効化。reload で反映）
show-completed: true     # 詳細表示で達成済みの条件も出すか（false なら未達成のみ）
hover-missing-limit: 10  # GUI 一覧のツールチップに出す「未達成」の最大件数（超過分は「ほか N 件」）
max-list: 80             # /ac <実績名> のチャット出力で各区分に列挙する最大件数（超過分は要約）
gui-rows: 6              # GUI（チェスト）の行数。最下段はナビ。2〜6（既定 6）
```

## 仕組み / 技術メモ

- `Server#advancementIterator()` で全実績を列挙し、**複数条件をすべて満たす AND 実績**（＝不足条件の列挙に意味があるもの）だけを抽出します。OR 実績やレシピ解禁（`recipes/`）・非表示の実績は除外します。
- 各プレイヤーについて `Player#getAdvancementProgress(advancement)` の `getAwardedCriteria()` / `getRemainingCriteria()` を読み、達成済み / 未達成を求めます。
- 条件キー（例 `minecraft:plains`）は Mob / アイテム / バイオームを判定して `Component.translatable(...)` に変換し、**クライアント言語**で表示します（フォールバックは整形キー）。
- GUI は `InventoryHolder` 実装のチェスト型インベントリで、クリックは**すべてキャンセル**（読み取り専用）。ナビゲーションのみ処理します。

### 制限

- 確認できるのは**オンラインの自分自身**のみです（`getAdvancementProgress` はオンライン Player が必要）。他人やオフラインプレイヤーの確認には対応しません。
- **バニラの実績画面（L キー）自体は改変できません**。説明文は実績定義に固定で、API は読み取り専用のためです。本プラグインは独自 GUI でこれを補います。
- 条件名のローカライズはクライアントの言語設定に依存します。サーバーが翻訳キーを解決できないクライアントでは、整形したキー名が表示されます。
- Paper 26.2 は experimental（alpha）です。API が変わる可能性があり、その際は追従が必要です。

## ビルド

```bash
./deploy.sh        # ローカルビルド（JDK 25 + Maven）。生成物: target/AchievementCheck-1.0.0.jar
# または
mvn -B clean package
```

`v*` タグを push すると GitHub Actions（`.github/workflows/build.yml`）がビルドし、リリースに jar を添付します。

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手は次の 2 通り（A・B）です。Docker（itzg/minecraft-server）を使う場合は、後述の「Docker Compose で自動ダウンロード」も利用できます。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/mc-achievement-check/releases) から最新の `AchievementCheck-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/mc-achievement-check --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/AchievementCheck-1.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/AchievementCheck-1.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/AchievementCheck-1.0.0.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

### Docker Compose（itzg/minecraft-server）で自動ダウンロード

[`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) イメージを使う場合は、jar を手元に用意しなくても **`PLUGINS` 環境変数にリリースの URL を並べるだけ**で、起動時に自動ダウンロードして `plugins/` に配置できます。

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/mc-achievement-check/releases/download/v1.0.0/AchievementCheck-1.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。バージョンを更新したら、URL の `v1.0.0` とファイル名を新しいリリースに合わせて変更してください。

起動ログに以下が出れば成功です。

```text
[AchievementCheck] AchievementCheck を有効化しました（状態: ON / 達成済み表示: あり）。
```

## ライセンス

MIT License — [LICENSE](LICENSE) を参照。
