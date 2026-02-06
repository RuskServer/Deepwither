# Contributing to Deepwither

Deepwitherプロジェクトへの貢献を検討していただきありがとうございます。
このドキュメントでは、開発者がコードベースに貢献する際の技術的なガイドラインとアーキテクチャについて説明します。

## 🏗 アーキテクチャガイドライン (Architecture Guidelines)

本プロジェクトでは、`onEnable` の肥大化を防ぎ、依存関係を安全に管理するために、独自の**依存関係解決システム**を採用しています。

### 依存関係管理システム (Dependency Injection System)

すべての主要な機能は「マネージャー (`Manager`)」として実装され、`ServiceManager` によって管理されます。`ServiceManager` は起動時に依存関係グラフ（トポロジカルソート）に基づいて適切な順序でマネージャーの初期化 (`init`) を行い、終了時には逆順で停止 (`shutdown`) させます。

### マネージャーの実装ルール

新しい機能を追加する場合、原則として `onEnable` に直接ロジックを書くことは禁止されています。以下のルールに従ってマネージャーを作成してください。

1.  **`IManager` インターフェースの実装**:
    すべてのマネージャーは `com.lunar_prototype.deepwither.util.IManager` を実装する必要があります。

2.  **`@DependsOn` アノテーションの付与**:
    そのマネージャーが依存している他のマネージャークラスを `@DependsOn` で宣言します。これにより、依存先の初期化が完了してから自身の `init()` が呼ばれることが保証されます。

3.  **コンストラクタインジェクション**:
    必要な依存オブジェクト（他のマネージャーやプラグインインスタンス）はコンストラクタ経由で受け取るように設計してください。

## 📝 新しい機能の追加手順 (How to Add a New Manager)

例として、`ExampleManager` を追加する手順を示します。

### 1. マネージャークラスの作成

```java
package com.lunar_prototype.deepwither.example;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

// 1. DatabaseManager に依存することを宣言
@DependsOn({DatabaseManager.class})
public class ExampleManager implements IManager {

    private final DatabaseManager db;

    // 2. 必要なインスタンスをコンストラクタで受け取る
    public ExampleManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() throws Exception {
        // 3. 初期化処理 (テーブル作成、データのロード、イベントリスナー登録など)
        // この時点で DatabaseManager.init() は完了していることが保証される
        db.getConnection().prepareStatement("...").execute();
    }

    @Override
    public void shutdown() {
        // 4. 終了処理 (データの保存など)
        // サーバー停止時に呼ばれる
    }
}
```

### 2. Deepwither.java への登録

`src/main/java/com/lunar_prototype/deepwither/Deepwither.java` の `setupManagers` メソッド内で登録します。

```java
private void setupManagers() {
    // ... 既存の登録 ...

    // register メソッドを使用して登録する
    // 戻り値を受け取ってフィールドに保持することも可能
    this.exampleManager = register(new ExampleManager(databaseManager));
}
```

これだけで、`ExampleManager` は適切なタイミングで自動的に `init()` および `shutdown()` されるようになります。

## 💾 データベースアクセス (Database Access)

データ永続化には `DatabaseManager` (SQLite) を使用します。
各マネージャーで個別にコネクションを作成せず、必ず `DatabaseManager` インスタンスを注入して使用してください。

*   **依存関係**: データベースを使用するクラスには必ず `@DependsOn({DatabaseManager.class})` を付与してください。
*   **非同期処理**: 重いクエリは `Bukkit.getScheduler().runTaskAsynchronously` 等を使用してメインスレッドをブロックしないようにしてください。

## 🚫 禁止事項

*   **`onEnable` への直接記述**: デバッグ目的以外で、`onEnable` メソッド内に直接ロジックを書くことは避けてください。
*   **手動初期化**: `manager.init()` を手動で呼び出さないでください。`ServiceManager` に任せてください。
*   **循環依存**: AがBに依存し、BがAに依存するような設計は避けてください。`ServiceManager` は循環依存を検出するとエラーをスローします。

---

このガイドラインに従うことで、Deepwitherのコードベースは堅牢かつ拡張しやすい状態に保たれます。
Happy Coding!
