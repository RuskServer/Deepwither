<div align="center">

# Deepwither

**マインクラフトサーバー開発組織 Echoes of Aether (RuskServer) 向けの次世代ゲームエンジン・プラグイン**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![GitHub stars](https://img.shields.io/github/stars/RuskServer/Deepwither?style=flat-square)](https://github.com/RuskServer/Deepwither/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/RuskServer/Deepwither?style=flat-square)](https://github.com/RuskServer/Deepwither/issues)
[![Website](https://img.shields.io/badge/Website-ruskserver.com-green.svg)](https://ruskserver.com/)

[Official Website](https://ruskserver.com/) | [Documentation](https://github.com/RuskServer/Deepwither/wiki) | [Security](https://github.com/RuskServer/Deepwither?tab=security-ov-file)

</div>

---

## 📖 概要 (Overview)

Deepwitherは、RuskServerが開発・運用している大規模MMORPGサーバー向けのコアエンジンとなるプラグインです。RPGの基幹システム（属性、スキル、アイテム、クエスト、ダンジョンなど）を包括的に管理します。

## 🏗️ プロジェクト構成 (Project Structure)

```text
src/main/java/com/lunar_prototype/deepwither/
├── api/              # 外部連携用および各種イベントAPI
├── booster/          # 経験値/通貨ブースターシステム
├── command/          # 各種コマンドの実装
├── companion/        # コンパニオン（ペット）システム
├── core/             # プラグインの基盤ロジック・キャッシュ管理
├── crafting/         # 製作（クラフト）システム
├── data/             # データクラスおよび設定管理
├── dungeon/          # ダンジョン管理システム
├── fasttravel/       # ファストトラベル機能
├── item/             # アイテムの独自実装と生成機能
├── layer_move/       # 階層移動および転送システム
├── loot/             # ドロップ・戦利品管理
├── modules/          # モジュール化された機能群
│   ├── aethelgard/   # 対話・NPC関連
│   ├── chat/         # チャット制御
│   ├── combat/       # 戦闘システム関連
│   ├── economy/      # 経済システム・ショップ
│   ├── infrastructure/# インフラ・データベース管理
│   ├── integration/  # 他プラグイン（MythicMobs等）連携
│   ├── mine/         # 採掘システム関連
│   ├── minidungeon/  # 小規模ダンジョン
│   ├── mob/          # カスタムモブフレームワーク
│   ├── outpost/      # 前哨基地システム
│   └── quest/        # クエスト管理
├── party/            # パーティシステム
├── profession/       # 職業（プロフェッション）システム
├── profiler/         # 戦闘分析・プロファイラー機能
├── raidboss/         # レイドボス管理
├── town/             # タウン（街）システム関連
├── util/             # 汎用ユーティリティ
└── Deepwither.java   # プラグインのエントリポイント（メインクラス）
```

## 🚀 Zed 拡張機能 (Deepwither Item Support)

アイテム製作とバランス調整を劇的に効率化するための、[Zed エディタ](https://zed.dev/)専用拡張機能が `deepwither-zed-extension/` に同梱されています。

### 🌟 主な機能
- **リアルタイムDPS分析:** カーソルを合わせるだけで、物理・遠距離・魔法それぞれの期待DPSを自動算出。
- **グローバルランキング:** ワークスペース内の全アイテムを自動走査し、現在のアイテムが全アイテム中何位か（上位何%か）を即座に表示。
- **ステータス別分析:** 攻撃力、HP、防御力など各項目ごとの順位と偏差を可視化（🥇, 🔥 などのインジケータ付き）。
- **階層別シンタックスハイライト:** アイテムID、プロパティ、数値を階層ごとに色分けし、複雑なYAML構造を直感的に把握可能。
- **StatType 補完:** `ATTACK_DAMAGE` や `CRIT_CHANCE` など、Deepwither 独自の全ステータス項目を強力に補完。

### 🛠️ 導入方法
1.  **LSPサーバーのビルド:**
    ```bash
    cd deepwither-zed-extension/server && cargo build --release
    ```
2.  **拡張機能（WASM）のビルド:**
    ```bash
    cd deepwither-zed-extension && cargo build --target wasm32-wasip1 --release
    ```
3.  **Zedへのインストール:**
    Zedのコマンドパレットから `Extensions: Install Dev Extension` を実行し、`deepwither-zed-extension` ディレクトリを選択してください。

※ パス内に `items/` を含むYAMLファイルを開くと自動的に有効化されます。

## 🛠️ サポートとポリシー (Support Policy)

本プロジェクトはOSSとして公開されていますが、**公式な個別サポートや動作保証は提供しておりません。**

- **個別対応:** 導入支援、設定代行、およびDM等でのトラブルシューティングは行っておりません。
- **自己責任:** 本プラグインの利用により生じた損害について、開発者は一切の責任を負いません。
- **AI Contributions:** コミットメッセージ等はAIによって自動生成されている箇所があるため、内容の正確性を保証するものではありません。

## 🐛 バグ・脆弱性の報告 (Reporting Issues)

### バグ報告
プログラムの不具合を発見した場合は、[GitHub Issues](https://github.com/RuskServer/Deepwither/issues) へ詳細を投稿してください。
※個別の返信や修正時期の確約はいたしかねますのでご了承ください。

### 脆弱性の報告
セキュリティ上の重大な欠陥を発見された場合は、Issueに公開せず、[リポジトリのSecurityタブ](https://github.com/RuskServer/Deepwither/security/advisories/new) からお知らせください。

## 📜 ライセンス (License)

このソフトウェアは **GNU Affero General Public License v3.0 (AGPL v3)** ライセンスのもとで公開されています。

> [!IMPORTANT]
> このプロジェクトはネットワークサーバー上で動作するソフトウェアであるため、修正を加えてサーバーで使用する場合でも、そのソースコードをAGPL v3に基づいて公開する義務が生じます。

<p align="center">
&copy; 2025-2026 RuskLabo (Lunar_prototype)
</p>
