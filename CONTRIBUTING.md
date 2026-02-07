# Contributing to Deepwither

Deepwitherプロジェクトへの貢献を検討していただきありがとうございます。
このドキュメントでは、開発者がコードベースに貢献する際の技術的なガイドラインとアーキテクチャについて説明します。

## 🏗 アーキテクチャガイドライン (Architecture Guidelines)

本プロジェクトでは、`onEnable` の肥大化を防ぎ、依存関係を安全に管理するために、独自の**依存関係解決システム**と**モダンなAPIアクセス層**を採用しています。

### 1. 依存関係管理システム (Dependency Injection System)

すべての主要な機能は「マネージャー (`Manager`)」として実装され、`ServiceManager` によって管理されます。`ServiceManager` は起動時に依存関係グラフ（トポロジカルソート）に基づいて適切な順序でマネージャーの初期化 (`init`) を行い、終了時には逆順で停止 (`shutdown`) させます。

### 2. スマートAPI (Smart API / DW Class)

開発効率とコードの可読性を最大化するため、`DW` クラスをエントリポイントとした **スタティック・ファサード** パターンを採用しています。

#### プレイヤー操作の自動補完 (Fluent API)
特定のプレイヤーに対して操作を行う場合、`DW.stats(player)` のように対象を先に指定することで、利用可能なメソッドが自動的に絞り込まれます。

```java
// 推奨される書き方
DW.stats(player).heal(10.0);           // HP回復
double hp = DW.stats(player).getHP();  // 現在のHP取得
DW.stats(player).update();             // ステータス更新
```

#### サービス・ロケーター (Service Locator)
インターフェースを指定するだけで、実装クラス（Manager）を自動的に取得できます。`DeepwitherAPI` にメソッドを手動で追加する必要はありません。

```java
// インターフェース名で取得（実装クラスを意識する必要がない）
IStatManager statAPI = DW.get(IStatManager.class);
```

## 📝 新しい機能の追加手順 (How to Add a New Manager)

Deepwitherに新しい機能（例：マナ管理システム）を追加する際の標準的な手順です。

### 1. APIインターフェースの定義
まず、`com.lunar_prototype.deepwither.api` 配下の適切なパッケージにインターフェースを作成します。これが外部（リスナーや他のプラグイン）から見える「窓口」になります。

```java
package com.lunar_prototype.deepwither.api.mana;

import org.bukkit.entity.Player;

public interface IManaManager {
    /** プレイヤーのマナを取得 */
    double getMana(Player player);
    
    /** マナを消費 */
    void consume(Player player, double amount);

    /** プレイヤー専用の操作コンテキストを返す（推奨） */
    PlayerMana of(Player player);

    interface PlayerMana {
        double get();
        void consume(double amount);
    }
}
```

### 2. マネージャークラスの実装
次に、`src/main/java/com/lunar_prototype/deepwither` 配下の内部パッケージで実装クラスを作成します。

```java
package com.lunar_prototype.deepwither.mana;

import com.lunar_prototype.deepwither.DatabaseManager;
import com.lunar_prototype.deepwither.api.mana.IManaManager;
import com.lunar_prototype.deepwither.util.DependsOn;
import com.lunar_prototype.deepwither.util.IManager;

// 1. IManager と定義したAPIインターフェースを実装
// 2. 依存関係を宣言（この場合 DatabaseManager が初期化された後に init が呼ばれる）
@DependsOn({DatabaseManager.class})
public class ManaManager implements IManaManager, IManager {

    private final DatabaseManager db;

    // 3. コンストラクタで依存オブジェクトを受け取る
    public ManaManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void init() throws Exception {
        // 4. 初期化処理 (リスナー登録、テーブル準備など)
    }

    @Override
    public void shutdown() {
        // 5. 終了処理 (データの保存など)
    }

    // --- APIインターフェースの実装 ---
    @Override
    public double getMana(Player player) { /* ... */ return 0; }

    @Override
    public PlayerMana of(Player player) {
        return new PlayerMana() {
            @Override public double get() { return getMana(player); }
            @Override public void consume(double amount) { /* ... */ }
        };
    }
}
```

### 3. Deepwither.java への登録
`setupManagers()` メソッド内で登録を行います。

```java
private void setupManagers() {
    // ...
    this.manaManager = register(new ManaManager(databaseManager));
}
```
※ `register()` メソッドを使うことで、`ServiceManager` が自動的に `IManaManager` インターフェースでも検索できるようにインデックスを張ります。

### 4. DW クラスへのショートカット追加（オプション）
頻繁に使用する機能であれば、`DW` クラスに短いアクセス用メソッドを追加します。

```java
// DW.java
public static IManaManager mana() {
    return get(IManaManager.class);
}

public static IManaManager.PlayerMana mana(Player player) {
    return mana().of(player);
}
```

これにより、開発者は `DW.mana(player).consume(10)` といった極めて簡潔なコードで新機能を利用できるようになります。

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

*   **`onEnable` への直接記述**: デバッグ目的以外で、`onEnable` メソッド内に直接ロジックを書くことは避けてください。
*   **手動初期化**: `manager.init()` を手動で呼び出さないでください。`ServiceManager` に任せてください。
*   **循環依存**: AがBに依存し、BがAに依存するような設計は避けてください。`ServiceManager` は循環依存を検出するとエラーをスローします。

---

このガイドラインに従うことで、Deepwitherのコードベースは堅牢かつ拡張しやすい状態に保たれます。
Happy Coding!
