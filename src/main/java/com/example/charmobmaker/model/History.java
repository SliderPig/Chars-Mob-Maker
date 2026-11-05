package com.example.charmobmaker.model;

import java.util.ArrayDeque;
import java.util.Deque;

// Undo and redo for the editor, keeping whole designs as text. Simple, and a design is small.
public final class History {
    private static final int MAX = 80;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String now;

    public History(String start) {
        now = start;
    }

    public void reset(String start) {
        undo.clear();
        redo.clear();
        now = start;
    }

    // Call after a finished edit. Nothing happens if nothing really changed.
    public boolean push(String state) {
        if (state.equals(now)) return false;
        undo.push(now);
        while (undo.size() > MAX) undo.removeLast();
        redo.clear();
        now = state;
        return true;
    }

    // The same state with a small change that shouldn't count as a step, like the id the server just gave it.
    public void replaceCurrent(String state) {
        now = state;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public String undo() {
        if (undo.isEmpty()) return null;
        redo.push(now);
        now = undo.pop();
        return now;
    }

    public String redo() {
        if (redo.isEmpty()) return null;
        undo.push(now);
        now = redo.pop();
        return now;
    }

    public String current() {
        return now;
    }
}
