package com.lunar_prototype.deepwither.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DailyTaskDataStore {
    CompletableFuture<DailyTaskData> loadTaskData(UUID playerId);
    void saveTaskData(DailyTaskData data);
}