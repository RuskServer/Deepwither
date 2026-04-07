package com.lunar_prototype.deepwither.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailMessage {

    private UUID id;
    private UUID recipientId;
    private String title;
    private List<String> bodyLines;
    private List<String> rewardItems;
    private long createdAt;

    public MailMessage() {
        // Gson 用の no-args constructor
    }

    public MailMessage(UUID recipientId, String title, List<String> bodyLines, List<String> rewardItems) {
        this.id = UUID.randomUUID();
        this.recipientId = recipientId;
        this.title = title;
        this.bodyLines = new ArrayList<>(bodyLines == null ? List.of() : bodyLines);
        this.rewardItems = new ArrayList<>(rewardItems == null ? List.of() : rewardItems);
        this.createdAt = System.currentTimeMillis();
        normalize();
    }

    public void normalize() {
        if (bodyLines == null) {
            bodyLines = new ArrayList<>();
        }
        if (rewardItems == null) {
            rewardItems = new ArrayList<>();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getBodyLines() {
        normalize();
        return bodyLines;
    }

    public List<String> getRewardItems() {
        normalize();
        return rewardItems;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
