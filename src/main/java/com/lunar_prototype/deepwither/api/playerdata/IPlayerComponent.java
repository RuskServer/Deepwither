package com.lunar_prototype.deepwither.api.playerdata;

/**
 * プレイヤーキャッシュに保存されるすべてのデータコンポーネントの共通インターフェース。
 * このインターフェースを実装することで、PlayerCacheで一元管理され、デバッグコマンド等から動的に参照できるようになります。
 */
public interface IPlayerComponent {
    
    /**
     * デバッグコマンド等で表示するための簡潔なサマリー文字列を返します。
     * @return データの概要を表す文字列
     */
    default String toDebugSummary() {
        return this.toString();
    }
}
