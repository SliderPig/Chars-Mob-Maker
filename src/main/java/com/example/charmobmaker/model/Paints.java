package com.example.charmobmaker.model;

// Small helpers for painted cells. A cell is 16 x 16, row by row, ARGB.
public final class Paints {
    public static final int CELL = 16;

    private Paints() {}

    public static void fillFrom(int[] cell, int x, int y, int colour) {
        if (x < 0 || y < 0 || x >= CELL || y >= CELL) return;
        int old = cell[y * CELL + x];
        if (old == colour) return;
        int[] stack = new int[CELL * CELL * 4];
        int top = 0;
        stack[top++] = y * CELL + x;
        while (top > 0) {
            int at = stack[--top];
            if (cell[at] != old) continue;
            cell[at] = colour;
            int ax = at % CELL, ay = at / CELL;
            if (ax > 0 && top < stack.length) stack[top++] = at - 1;
            if (ax < CELL - 1 && top < stack.length) stack[top++] = at + 1;
            if (ay > 0 && top < stack.length) stack[top++] = at - CELL;
            if (ay < CELL - 1 && top < stack.length) stack[top++] = at + CELL;
        }
    }

    // Which painted cell a face of a cube shows, -1 = none
    public static int cellFor(Part p, int face) {
        if (p.facePaint != null && face >= 0 && face < p.facePaint.length && p.facePaint[face] >= 0) return p.facePaint[face];
        return p.paint;
    }

    public static boolean usesPaint(Part p) {
        if (p.paint >= 0) return true;
        if (p.facePaint != null) for (int f : p.facePaint) if (f >= 0) return true;
        return false;
    }
}
