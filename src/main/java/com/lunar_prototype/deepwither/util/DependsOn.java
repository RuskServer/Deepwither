package com.lunar_prototype.deepwither.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * サービスの依存関係を定義するアノテーション。
 * 初期化順序の制御に使用されます。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DependsOn {
    Class<? extends IManager>[] value();
}
