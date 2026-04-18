package com.lunar_prototype.deepwither.item.processor;

public interface ItemProcessor {
    /**
     * プロセッサがアイテムロードコンテキストを処理します。
     * コンテキストが invalid に設定された場合、後続のプロセッサはスキップされるべきです。
     *
     * @param context アイテムのロードコンテキスト
     */
    void process(ItemLoadContext context);
}
