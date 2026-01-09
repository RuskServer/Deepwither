package com.lunar_prototype.deepwither.seeker.v2;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ProfileLoader {
    private final Map<String, BehaviorProfile> registry = new HashMap<>();
    private final Gson gson = new Gson();

    public void loadAll(File folder) {
        if (!folder.exists()) folder.mkdirs();
        for (File file : folder.listFiles((d, name) -> name.endsWith(".json"))) {
            try (FileReader reader = new FileReader(file)) {
                BehaviorProfile profile = gson.fromJson(reader, BehaviorProfile.class);
                registry.put(profile.profile_id, profile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public BehaviorProfile getProfile(String id) {
        return registry.get(id);
    }
}