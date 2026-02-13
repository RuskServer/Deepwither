package com.lunar_prototype.deepwither.api.crafting;

import org.jspecify.annotations.NonNull;

/**
 * @see Fabricator
 */
public sealed interface FabricationResult<Result> permits FabricationResult.Failure, FabricationResult.Success {
    static <Result> @NonNull FabricationResult<Result> failure(Reason reason) {
        return new Failure<>(reason);
    }

    static <Result> @NonNull FabricationResult<Result> success(Result result) {
        return new Success<>(result);
    }

    record Failure<Result>(Reason reason) implements FabricationResult<Result> {}
    record Success<Result>(Result result) implements FabricationResult<Result> {}

    enum Reason {
        UNKNOWN
    }
}
