package com.lunar_prototype.deepwither.api.crafting;

import org.jspecify.annotations.NullMarked;

/**
 * レシピ処理用の(過度に)抽象的なインターフェース
 *
 * @apiNote ドメイン固有DTOを使用すると幸せになります。
 * <br> 少なくとも、将来見たときに意味不明な型パラメータを見て発狂することは回避できるかもしれない。
 *
 * @implNote 実装方法 (is-a, has-a, etc...) は指定しません。
 * <br> 継承したインターフェースを作るなり、パラメータ用DTOをぶちこむなりして扱って。
 * <br> 意味を無視した使い方も許容されるけど、{@link java.util.function.Function} の方がおすすめですよ。
 *
 * @param <Recipe> 入力(材料, 要求, コンテキストなど; 要するになんでも！)
 * @param <Result> {@code nya? nya? nyo! nya??}
 */
@NullMarked
public interface Fabricator<Recipe, Result> {
    FabricationResult<Result> fabricate(Recipe recipe);
}
