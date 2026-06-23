package com.atitai.posture.domain;

import java.util.ArrayList;
import java.util.List;

public class AdviceBlock {

    private String summary;
    private List<String> suggestions = new ArrayList<String>();
    private List<String> warnings = new ArrayList<String>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}

