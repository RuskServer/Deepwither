package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.FabricationGrade;
import java.util.Map;

public class CraftingRecipe {
    private final String id;
    private final String resultItemId;
    private final int timeSeconds;
    private final Map<String, Integer> ingredients;

    /** @deprecated 等級システム廃止のため grade 引数は無視される。 */
    @Deprecated
    public CraftingRecipe(String id, String resultItemId, int timeSeconds, Map<String, Integer> ingredients, FabricationGrade grade) {
        this(id, resultItemId, timeSeconds, ingredients);
    }

    public CraftingRecipe(String id, String resultItemId, int timeSeconds, Map<String, Integer> ingredients) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.timeSeconds = timeSeconds;
        this.ingredients = ingredients;
    }

    public String getId() { return id; }
    public String getResultItemId() { return resultItemId; }
    public int getTimeSeconds() { return timeSeconds; }
    public Map<String, Integer> getIngredients() { return ingredients; }

    /** 等級システム廃止のため常に STANDARD を返す。 */
    @Deprecated
    public FabricationGrade getGrade() { return FabricationGrade.STANDARD; }
}