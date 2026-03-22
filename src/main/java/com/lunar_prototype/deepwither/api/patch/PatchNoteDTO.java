package com.lunar_prototype.deepwither.api.patch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PatchNoteDTO {
    private final String title;
    private final String date;
    private final String content;
    private final List<String> tags;

    public PatchNoteDTO(String title, String content, List<String> tags) {
        this.title = title;
        this.date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        this.content = content;
        this.tags = tags;
    }

    public String getTitle() {
        return title;
    }

    public String getDate() {
        return date;
    }

    public String getContent() {
        return content;
    }

    public List<String> getTags() {
        return tags;
    }
}
