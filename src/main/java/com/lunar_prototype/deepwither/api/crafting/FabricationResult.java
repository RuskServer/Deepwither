package com.lunar_prototype.deepwither.api.crafting;

import org.jspecify.annotations.NullMarked;

/**
 * @see Fabricator
 */
@NullMarked
public sealed interface FabricationResult<Result> permits FabricationResult.Failure, FabricationResult.Success {
    static <Result> FabricationResult<Result> failure(Reason reason) {
        return new Failure<>(reason);
    }

    static <Result> FabricationResult<Result> success(Result result) {
        return new Success<>(result);
    }

    record Failure<Result>(Reason reason) implements FabricationResult<Result> {}
    record Success<Result>(Result result) implements FabricationResult<Result> {}

    enum Reason {
        UNKNOWN
    }
}
