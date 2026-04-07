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
├── aethelgard/       # クエスト・対話システム
├── api/              # 外部連携用API
├── booster/          # 経験値/通貨ブースター
├── command/          # 各種コマンド実装
├── companion/        # コンパニオンシステム
├── core/             # プラグインの基盤ロジック
├── crafting/         # 製作システム
├── dungeon/          # ダンジョンシステム
├── loot/             # ドロップ・戦利品管理
├── modules/          # モジュール化された機能群
│   ├── combat/       # 戦闘システム関連
│   ├── core/         # モジュール基盤
│   ├── dynamic_quest/# 動的クエスト生成
│   ├── economy/      # 経済システム
│   ├── infrastructure/# インフラ・データ管理
│   ├── integration/  # 他プラグイン連携
│   ├── mob/          # モブ動作・管理
│   ├── quest/        # 定常クエスト
│   └── rune/         # ルーンシステム
├── profession/       # 職業システム
├── SkilltreeManager  # スキルシステム
├── StatManager       # ステータス・属性管理
├── util/             # ユーティリティ
├── Deepwither        # メインクラス
└── ItemFactory       # アイテム生成エンジン
```

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
