# Contributing to Deepwither

Deepwitherプロジェクトへの貢献を検討していただきありがとうございます。
このドキュメントでは、開発者がコードベースに貢献する際の技術的なガイドラインとアーキテクチャについて説明します。

[📖 Javadocs (API Reference)](https://ruskserver.github.io/Deepwither/)

[🧭 全機能統合シーケンス図](docs/system-sequence-diagram.md)

## 🏗 アーキテクチャガイドライン (Architecture Guidelines)

本プロジェクトでは、拡張性と保守性を高めるために **Modular Monolith** アーキテクチャを採用しています。
機能は「モジュール (`Module`)」単位で分割され、`ServiceContainer` (DIコンテナ) によって疎結合に管理されます。

### 1. モジュールシステム (Module System)
すべての機能は `IModule` インターフェースを実装したモジュールクラスとして定義されます。
モジュールには以下のライフサイクルがあります：

- **configure(ServiceContainer container)**: 
    - 自身が提供するサービス（Manager等）をコンテナに登録します。
    - 他のモジュールが登録したサービスに依存してはいけません（まだ登録されていない可能性があるため）。
- **start()**:
    - コンテナから依存するサービスを取得し、初期化処理を行います。
    - イベントリスナーの登録やタスクのスケジュールなど、機能の有効化を行います。
- **stop()**:
    - 機能の停止、リソースの解放を行います。

### 2. 依存関係注入 (Dependency Injection)
`ServiceContainer` を使用して、コンストラクタインジェクションにより依存関係を解消します。
`Deepwither.getManager()` のような静的アクセサの使用は推奨されません（互換性のために一部残っていますが `@Deprecated` です）。

#### 手動登録 (Manual Registration)
明示的に `new` してインスタンスを登録する方法です。

```java
// コンテナへの登録 (Module.configure内)
container.registerInstance(MyManager.class, new MyManager(plugin));
```

#### 自動解決 (Auto-wiring)
コンテナにクラスの生成を任せることで、依存関係を自動的に注入できます。
コンストラクタの引数に指定された型がコンテナ内に存在すれば、自動的に渡されます。

```java
// Module.start内などで取得する際、未生成なら依存関係を解決して生成される
MyManager manager = container.get(MyManager.class);
```
※ 自動解決を使う場合、依存先のインスタンスが先に `configure` されているか、あるいは具象クラスとして解決可能である必要があります。

---

## 📝 新しい機能の追加手順 (How to Add a New Module/Manager)

### 1. モジュールの作成
`com.lunar_prototype.deepwither.modules.[feature_name]` パッケージを作成し、`IModule` を実装したクラスを作成します。

```java
public class MyFeatureModule implements IModule {
    private final Deepwither plugin;

    public MyFeatureModule(Deepwither plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configure(ServiceContainer container) {
        // Managerの登録
        container.registerInstance(MyManager.class, new MyManager(plugin));
    }

    @Override
    public void start() {
        // ...
    }
    
    @Override
    public void stop() {
        // ...
    }
}
```

### 2. ブートストラップへの登録
`com.lunar_prototype.deepwither.core.engine.DeepwitherBootstrap` の `registerModules()` メソッドに、作成したモジュールを追加します。

```java
moduleManager.registerModule(new MyFeatureModule(plugin));
```

### 2.1 登録処理の集約
リスナー登録やコマンド登録は、`ModuleRegistrar` を通して行うことを推奨します。
これにより、モジュール内に登録処理を閉じ込めつつ、解除や重複管理を一箇所へ集約できます。

### 3. Managerの実装と自動ライフサイクル (Manager Implementation)
Managerは、モジュール内のロジックをカプセル化したクラスです。
ライフサイクル管理のために `com.lunar_prototype.deepwither.util.IManager` インターフェースの実装を強く推奨します。

新アーキテクチャでは、**モジュールの `start()` 内で `manager.init()` を手動で呼び出してはいけません。**
`IManager` を実装したクラスを `ServiceContainer` に登録しておけば、プラグイン起動時にコンテナが依存関係グラフを構築し、**正しい順番で全マネージャーの `init()` を自動的に一括実行**します。

```java
import com.lunar_prototype.deepwither.util.IManager;

// 依存関係はコンストラクタ引数から自動抽出され、初期化順序が決定されます。
// 暗黙的な依存（イベント順序など）がある場合のみ @DependsOn を使用します。
public class MyManager implements IManager {

    private final Deepwither plugin;
    private final DatabaseManager dbManager;

    // ServiceContainer が DatabaseManager を自動解決・注入し、
    // DatabaseManager の init() が終わった後にこのクラスの init() を呼び出します。
    public MyManager(Deepwither plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    /**
     * 初期化処理 (onEnable相当)
     * イベントリスナーの登録や、データのロードを行います。
     * 手動で呼び出さないでください。
     */
    @Override
    public void init() throws Exception {
        plugin.getLogger().info("MyManager initialized!");
    }

    /**
     * 終了処理 (onDisable相当)
     * データの保存やリソースの解放を行います。
     * これもコンテナから自動で呼び出されます。
     */
    @Override
    public void shutdown() {
        plugin.getLogger().info("MyManager shutdown!");
    }
}
```

## 💾 データベースアクセス (Database Access)

データ永続化には `IDatabaseManager` を使用します。
本プロジェクトでは、開発者がSQLの定型文（接続取得、例外処理、クローズ漏れ）に悩まされないよう、**高レベルの抽象化API**を提供しています。

### 基本的な使い方
`DW.db()` を介して、以下のメソッドを利用できます。

#### 1. データの更新・挿入 (execute)
`INSERT`, `UPDATE`, `DELETE` クエリを実行します。パラメータは可変長引数で渡せます。

```java
// データの更新例
DW.db().execute(
    "UPDATE player_levels SET level = ? WHERE uuid = ?",
    newLevel, player.getUniqueId().toString()
);
```

#### 2. 単一データの取得 (querySingle)
1行だけ結果を取得する場合に使用します。結果は `Optional` で返されます。

```java
// 単一データの取得例
Optional<Integer> level = DW.db().querySingle(
    "SELECT level FROM player_levels WHERE uuid = ?",
    rs -> rs.getInt("level"),
    player.getUniqueId().toString()
);
```

#### 3. 複数データの取得 (queryList)
複数行の結果をリストとして取得する場合に使用します。

```java
// 複数データの取得例
List<String> clanNames = DW.db().queryList(
    "SELECT name FROM clans WHERE owner = ?",
    rs -> rs.getString("name"),
    player.getUniqueId().toString()
);
```

### 注意事項
*   **インターフェースの使用**: 各マネージャーで個別にコネクションを作成せず、必ず `DW.db()` または注入された `IDatabaseManager` を使用してください。
*   **依存関係**: データベースを使用するクラスには必ず `@DependsOn({DatabaseManager.class})` を付与してください。
*   **非同期処理**: 重いクエリや大量のバッチ処理を行う場合は、`runAsync` や `supplyAsync` を使用してメインスレッドをブロックしないようにしてください。

## 🚫 禁止事項

*   **Deepwitherクラスへのゲッター追加**: `Deepwither.java` にこれ以上 `public Manager getManager()` のようなメソッドを追加しないでください。新しい機能は `ServiceContainer` 経由で取得するか、モジュール内で完結させる必要があります。既存のゲッターは互換性のために残されていますが、新規追加は厳禁です。
*   **`onEnable` への直接記述**: デバッグ目的以外で、`onEnable` メソッド内に直接ロジックを書くことは避けてください。
*   **手動初期化**: `manager.init()` および `manager.shutdown()` をモジュールや別クラスから**絶対に手動で呼び出さないでください**。二重初期化のバグを引き起こします。`ServiceContainer` の自動ライフサイクルに完全に任せてください。
*   **循環依存**: AがBに依存し、BがAに依存するような設計は避けてください。`ServiceContainer` は循環依存を検出するとグラフ構築時に例外をスローし、起動を停止します。

---

このガイドラインに従うことで、Deepwitherのコードベースは堅牢かつ拡張しやすい状態に保たれます。
Happy Coding!
