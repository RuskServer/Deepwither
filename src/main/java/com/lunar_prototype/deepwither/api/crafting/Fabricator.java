package com.lunar_prototype.deepwither.api.crafting;

/**
 * レシピ処理用の(過度に)抽象的なインターフェース
 *
 * @apiNote ドメイン固有DTOを使用すると幸せになります。
 * <br> 少なくとも、将来見たときに意味不明な型パラメータを見て発狂することは回避できるかもしれない。
 *
 * @param <Recipe> 入力(材料, 要求, コンテキストなど; 要するになんでも！)
 * @param <Result> {@code nya? nya? nyo! nya??}
 */
public interface Fabricator<Recipe, Result> {
    /**
     * @param recipe Must: Immutable
     */
    FabricationResult<Result> simulate(Recipe recipe);

    /**
     * @param recipe Should: Mutable
     * @return 同一の状態, 入力であれば {@link #simulate(Recipe)} と同一の結果を返すことが望ましい。
     * <br> ただし、目的によっては異なる結果を返してもよい。
     */
    FabricationResult<Result> fabricate(Recipe recipe);
}
