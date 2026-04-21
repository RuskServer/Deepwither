package com.lunar_prototype.deepwither.crafting;

import com.lunar_prototype.deepwither.FabricationGrade;
import java.util.Map;

public class CraftingRecipe {
    private final String id;
    private final String resultItemId;
    private final int timeSeconds;
    private final Map<String, Integer> ingredients;
    private final int requiredCraftLevel;

    /** @deprecated 等級システム廃止のため grade 引数は無視される。 */
    @Deprecated
    public CraftingRecipe(String id, String resultItemId, int timeSeconds, Map<String, Integer> ingredients, FabricationGrade grade) {
        this(id, resultItemId, timeSeconds, ingredients, 0);
    }

    public CraftingRecipe(String id, String resultItemId, int timeSeconds, Map<String, Integer> ingredients) {
        this(id, resultItemId, timeSeconds, ingredients, 0);
    }

    public CraftingRecipe(String id, String resultItemId, int timeSeconds, Map<String, Integer> ingredients, int requiredCraftLevel) {
        this.id = id;
        this.resultItemId = resultItemId;
        this.timeSeconds = timeSeconds;
        this.ingredients = ingredients;
        this.requiredCraftLevel = Math.max(0, requiredCraftLevel);
    }

    public String getId() { return id; }
    public String getResultItemId() { return resultItemId; }
    public int getTimeSeconds() { return timeSeconds; }
    public Map<String, Integer> getIngredients() { return ingredients; }
    public int getRequiredCraftLevel() { return requiredCraftLevel; }

    /** 等級システム廃止のため常に STANDARD を返す。 */
    @Deprecated
    public FabricationGrade getGrade() { return FabricationGrade.STANDARD; }
}