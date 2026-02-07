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

例として、`ManaManager` を追加し、APIとして公開する手順を示します。

### 1. インターフェースの定義 (API層)
`api` パッケージの下にインターフェースを作成します。

```java
package com.lunar_prototype.deepwither.api.mana;

public interface IManaManager extends IManager {
    double getMana(Player player);
    void consume(Player player, double amount);
}
```

### 2. マネージャークラスの実装 (内部層)
インターフェースを実装します。

```java
@DependsOn({DatabaseManager.class})
public class ManaManager implements IManaManager {
    // ... 実装 ...
}
```

### 3. Deepwither.java への登録
インターフェースを実装したクラスを登録すると、`ServiceManager` が自動的にインターフェースでも引けるようにインデックスを作成します。

```java
private void setupManagers() {
    this.manaManager = register(new ManaManager(databaseManager));
}
```

これで、即座に `DW.get(IManaManager.class)` で呼び出し可能になります。

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
