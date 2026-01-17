package com.lunar_prototype.deepwither.internal.pdc;

import com.lunar_prototype.deepwither.internal.key.KeyUtil;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link PersistentDataContainer} へのアクセスを抽象化するクラス
 * <p>
 * キーとデータ型をカプセル化してpdcへの読み書きを簡素化する<br>
 * 少なくともキーとデータ型が食い違う面倒なことを減らせるはず<br>
 * ついでに古いデータから移行するための機能付き<br>
 * </p>
 * @param <P> {@link PersistentDataType} を見てね♡
 * @param <C> {@link PersistentDataType} を見ろ♡
 */
public final class PdcAccessor<P, C> {
    private final NamespacedKey key;
    private final PersistentDataType<P, C> type;
    private final List<Migrator<?, C>> migrators;

    private PdcAccessor(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type) {
        this.key = key;
        this.type = type;
        this.migrators = new ArrayList<>();
    }

    @Contract(value = "_, _ -> new", pure = true)
    public static <P, C> @NotNull PdcAccessor<P, C> of(@NotNull NamespacedKey key, @NotNull PersistentDataType<P, C> type) {
        return new PdcAccessor<>(key, type);
    }

    @Contract("_, _ -> new")
    public static <P, C> @NotNull PdcAccessor<P, C> of(@NotNull String key, @NotNull PersistentDataType<P, C> type) {
        return PdcAccessor.of(KeyUtil.of(key), type);
    }

    public NamespacedKey key() {
        return this.key;
    }

    public PersistentDataType<P, C> type() {
        return this.type;
    }

    /**
     * 移行元となる古いやつを登録
     * <p>
     * {@link #migrate(PersistentDataHolder)} が呼び出された時にデータがなければ、ここで登録された古いアクセサからデータの抽出を試みる
     * </p>
     *
     * @param accessor 旧データにアクセスするためのやつ
     * @param migrator 旧データを現在の型に変換する関数
     * @param <T> 旧データの型({@code <C>}の部分)
     * @return self
     */
    public <T> PdcAccessor<P, C> from(@NotNull PdcAccessor<?, T> accessor, Function<? super T, ? extends C> migrator) {
        this.migrators.add(new Migrator<>(accessor, migrator));
        return this;
    }

    /**
     * ただのらっぱー
     */
    public @NotNull Optional<C> get(@NotNull PersistentDataHolder holder) {
        return Optional.ofNullable(holder.getPersistentDataContainer().get(this.key(), this.type()));
    }

    /**
     * ただのらっぱー
     */
    public void set(@NotNull PersistentDataHolder holder, @NotNull C value) {
        holder.getPersistentDataContainer().set(this.key(), this.type(), value);
    }

    /**
     * 問題なく取得できるか調べるやつ
     * <p>
     * {@link #get(PersistentDataHolder)} 使った方が早い
     * </p>
     */
    public boolean hasDecodableEntry(@NotNull PersistentDataHolder holder) {
        return this.get(holder).isPresent();
    }

    /**
     * 格納されたデータの型が同じかしらべるやつ
     * <p>
     * {@code <P>} の部分の判定しかできないよ<br>
     * {@link PersistentDataContainerView#has(NamespacedKey, PersistentDataType)} を読んでるだけ
     * </p>
     */
    public boolean hasCompatibleEntry(@NotNull PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(this.key(), this.type());
    }

    /**
     * 同じキーが存在するかだけ調べる
     * <p>
     * 競合とかしらべるtきにつかうんじゃないの
     * </p>
     */
    public boolean hasSameKey(@NotNull PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(this.key());
    }

    /**
     * getとsetを使うのめんどくさいので追加
     */
    public void update(@NotNull PersistentDataHolder holder, @NotNull Function<C, C> updater) {
        this.set(holder, updater.apply(holder.getPersistentDataContainer().get(this.key(), this.type())));
    }

    /**
     * ただのらっぱー
     */
    public void remove(@NotNull PersistentDataHolder holder) {
        holder.getPersistentDataContainer().remove(this.key());
    }

    /**
     * 移行用の機能
     * <p>
     * ふるいでーたはきえないよ<br>
     * 移行ミスった時に消えられても困る
     * </p>
     */
    public void migrate(@NotNull PersistentDataHolder holder) {
        var container = holder.getPersistentDataContainer();
        if (container.has(this.key(), this.type())) return;

        var migrated = migrate(container);
        if (migrated != null) {
            container.set(this.key(), this.type(), migrated);
        }
    }

    private @Nullable C migrate(@NotNull PersistentDataContainer container) {
        for (var migrator : this.migrators) {
            var result = migrateHelper(container, migrator);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private <T> @Nullable C migrateHelper(@NotNull PersistentDataContainer container, @NotNull Migrator<T, C> migrator) {
        var accessor = migrator.accessor();
        var old1 = container.get(accessor.key(), accessor.type());
        if (old1 == null) {
            var old2 = accessor.migrate(container);
            if (old2 != null) {
                return migrator.migrator().apply(old2);
            }
            return null;
        }
        return migrator.migrator().apply(old1);
    }

    private record Migrator<O, N>(PdcAccessor<?, O> accessor, Function<? super O, ? extends N> migrator) {}
}

