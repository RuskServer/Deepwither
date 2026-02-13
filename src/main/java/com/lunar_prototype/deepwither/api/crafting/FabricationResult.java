package com.lunar_prototype.deepwither.api.crafting;

/**
 * @see Fabricator
 */
public sealed interface FabricationResult<Result> permits FabricationResult.Failure, FabricationResult.Success {
    static <Result> FabricationResult<Result> failure(Reason reason) {
        return new Failure<>(reason);
    }

    static <Result> FabricationResult<Result> success(Result result) {
        return new Success<>(result);
    }

    record Failure<Result>(Reason reason) implements FabricationResult<Result> {}
    record Success<Result>(Result result) implements FabricationResult<Result> {}

    /**
     * 必要に応じて追加, 整理していい失敗理由。
     */
    enum Reason {
        UNKNOWN
    }
}
