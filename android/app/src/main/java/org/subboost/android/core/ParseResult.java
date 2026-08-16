package org.subboost.android.core;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ParseResult {
    private final List<Map<String, Object>> nodes;
    private final List<String> errors;

    public ParseResult(List<Map<String, Object>> nodes, List<String> errors) {
        this.nodes = new ArrayList<>(nodes);
        this.errors = new ArrayList<>(errors);
    }

    public List<Map<String, Object>> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}
