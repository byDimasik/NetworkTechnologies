package ru.nsu.fit.g15205.shishlyannikov;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Work implements Serializable {
    private int[] range = new int[2];
    private Map<String, String> results = new HashMap<>(); // hash : actg-string

    public Work(int start, int end) {
        range[0] = start;
        range[1] = end;
    }

    public int getStart() {
        return range[0];
    }

    public int getEnd() {
        return range[1];
    }

    public void addResult(String hash, String string) {
        results.put(hash, string);
    }

    public Map<String, String> getResults() {
        return results;
    }
}
