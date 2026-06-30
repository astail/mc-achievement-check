# CLAUDE.md

Claude がこのリポジトリで作業する際の開発メモ（Paper プラグイン）。

## プラグインの目的

AchievementCheck は、各プレイヤーが `/ac` で**自分のバニラ実績（advancement）の達成状況と「不足している条件」**
（未訪問バイオーム・未達成の食料・未討伐モブなど）を確認できるようにする。
複数条件のバニラ実績は達成状況がサーバーに保存されているが、ゲーム内の実績画面では残りの具体項目が分からない。
そこを補うのが本プラグイン。確認系のみで進捗の改変は行わず、サーバー側だけで動く（クライアント MOD 不要）。

## ビルド要件

- Java 25 + Maven。生成物は `AchievementCheck-1.0.0.jar`。
- 唯一の依存は `io.papermc.paper:paper-api:26.2.build.40-alpha`（provided）。
- ローカルビルドは `./deploy.sh`（JDK 25 + Maven）または `mvn -B clean package`。

## アーキテクチャ構成

- **AchievementCheckPlugin**: 本体（`JavaPlugin` 兼 `Listener`）。config 読込、コマンド/リスナー登録、
  GUI の `InventoryClickEvent` / `InventoryDragEvent` を読み取り専用化。`onDisable` で開いている自前 GUI を閉じる。
- **AchievementCheckCommand**: `/ac`（GUI 起動）・`/ac <実績名>`（チャット詳細）・`/ac reload` の実処理とタブ補完。
- **AdvancementReader**: 中核。実績の列挙・コレクション判定・進捗集計・条件名（translatable）解決。
  Bukkit 非依存の純粋関数（`humanize` / `stripNamespace` / `isAndCollection`）を切り出してテスト可能にしている。
- **AchievementGui**: `InventoryHolder` 実装のチェスト型 GUI。一覧/詳細の構築とページ送り、クリック処理。

## 設計上の要点

- **読み取りは公式 API のみ**: `Server#advancementIterator()` で全実績を列挙し、各プレイヤーは
  `Player#getAdvancementProgress(adv)` の `getAwardedCriteria()` / `getRemainingCriteria()` / `isDone()` を読む。
  NMS やパケット改変は使わない（堅牢・バージョン追従に強い）。
- **コレクション系の判定（OR 実績の除外）**: 「不足条件の列挙に意味がある」のは全条件 AND の複数条件実績だけ。
  AND 実績は「条件ごとに 1 要件グループ」になるため、`getRequirements().getRequirements().size() == getCriteria().size()`
  かつ条件数 > 1 で判別する（`AdvancementReader.isAndCollection`）。OR 実績（要件 1 グループ）はこれで除外される。
  さらに `getDisplay() == null`（レシピ解禁など非表示）と キーが `recipes/` のものを除外する。
- **条件名のローカライズ**: 条件キー（例 `minecraft:plains`）を `Registry.ENTITY_TYPE` → `Material.matchMaterial` の
  順で解決し、Mob は `entity.*`、アイテム/ブロックは `block./item.*`、それ以外（バイオーム等）は `biome.<ns>.<path>` の
  translation key を組む。`Component.translatable(key, fallback)` で送るので、クライアント言語で表示され、
  翻訳できないときは `humanize` した整形キー名にフォールバックする。実績タイトルは `getDisplay().title()`（Component）を
  そのまま使う。
- **GUI は読み取り専用**: チェスト型インベントリを `InventoryHolder`（`AchievementGui`）で識別し、`InventoryClickEvent` は
  常に `setCancelled(true)`。ナビゲーション（ページ送り・詳細遷移・戻る・閉じる）以外は何もしない。画面遷移のたびに
  新しいインスタンス＝新しい `Inventory` を生成して開き直す（状態管理を単純化）。アイコンは `getDisplay().icon()` を
  clone して使い、詳細のアイテム系条件は実アイテムを、バイオーム/モブは染料マーカーをアイコンにする。
- **対象はオンラインの自分のみ**: `getAdvancementProgress` がオンライン Player を要求するため。`/ac <実績名>` と GUI は
  Player 専用。`reload` のみコンソール可。
- **権限は 2 段階**: 確認は `achievementcheck.use`（既定 true）。`reload` は `achievementcheck.manage`（既定 op）。

## 既知の制限 / 注意

- オンラインの自分のみ確認可（他人・オフラインは非対応）。
- バニラの実績画面（L キー）自体は改変できない（説明文は実績定義に固定・API 読取専用）。独自 GUI で代替している。
- 条件名のローカライズはクライアント言語依存。翻訳不可なクライアントでは整形キー名が出る。
- **Paper 26.2 は experimental（alpha）**。レジストリ / Translatable / Advancement API の変更があり得るため、
  ビルド時に最新ビルド番号と Javadoc を確認し、`translationKeyFor` のフォールバックは必ず残すこと。

## 検証メモ

- `mvn -B clean package` でビルド＆JUnit（`AdvancementReaderTest`）。純粋関数（humanize / isAndCollection 等）を検証。
- 実ロード確認は Docker `itzg/minecraft-server`（`TYPE=PAPER` / `VERSION=26.2` / `PAPER_CHANNEL=experimental` /
  `EULA=TRUE`）に jar をマウントして起動し、`Enabling AchievementCheck` と自前ログ、`plugins` 一覧での有効表示を確認。
- GUI / チャットの実出力は実クライアントが必要なため手動確認（未訪問バイオームでログインして `/ac` の表示を目視）。

## リリース手順

- セマンティックバージョニング。`v*` タグの push で GitHub Actions（`.github/workflows/build.yml`）がビルドし、
  `gh release create --generate-notes` で jar を添付する。
- サーバーへの配置（Releases から DL、または Docker `itzg/minecraft-server` の `PLUGINS` 環境変数で自動 DL）は
  README の「サーバーへの配置」を参照。
- バージョンを上げるときは `pom.xml` の `<version>` と `plugin.yml` の `version`、README / deploy.sh のファイル名を揃える。
