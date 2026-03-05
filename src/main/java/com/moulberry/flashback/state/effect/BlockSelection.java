package com.moulberry.flashback.state.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a selection of blocks in the world.
 *
 * Selection syntax:
 * - {@code (x1,y1,z1..x2,y2,z2)}: cuboid region
 * - {@code (x,y,z)}: single block
 * - {@code selection1 + selection2 + ...}: union
 * - {@code selection1 - selection2}: difference
 * - {@code *}: all blocks
 * - {@code selection[filter]}: filtered selection
 * - {@code (selection)}: parenthesized selection
 */
public class BlockSelection {

    private final String raw;
    private final List<Region> regions;
    private final boolean allBlocks;
    private final SelectionExpr selectionExpr;
    private final SelectionExpr geometryExpr;

    private BlockSelection(String raw, List<Region> regions, boolean allBlocks, SelectionExpr selectionExpr, SelectionExpr geometryExpr) {
        this.raw = raw;
        this.regions = Collections.unmodifiableList(regions);
        this.allBlocks = allBlocks;
        this.selectionExpr = selectionExpr;
        this.geometryExpr = geometryExpr;
    }

    public String getRaw() {
        return this.raw;
    }

    public List<Region> getRegions() {
        return this.regions;
    }

    public boolean isAllBlocks() {
        return this.allBlocks;
    }

    /**
     * Check if a block position is contained within this selection.
     * This check ignores block-state filters.
     */
    public boolean contains(BlockPos pos) {
        return this.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(int x, int y, int z) {
        return this.geometryExpr.test(null, x, y, z);
    }

    /**
     * Check if a block position is selected including block-state filters.
     */
    public boolean matches(ServerLevel level, BlockPos pos) {
        return this.matches(level, pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean matches(ServerLevel level, int x, int y, int z) {
        return this.selectionExpr.test(level, x, y, z);
    }

    public boolean isEmpty() {
        return !this.allBlocks && this.selectionExpr == SelectionExpr.FALSE;
    }

    /**
     * Parse a selection string into a BlockSelection.
     * Returns an empty selection if parsing fails.
     */
    public static BlockSelection parse(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }

        String trimmed = text.trim();
        Parser parser = new Parser(trimmed);
        Expr expr = parser.parseSelection();
        if (expr == null || !parser.isFullyConsumed()) {
            return empty();
        }

        List<Region> regions = new ArrayList<>();
        boolean representableAsUnion = expr.collectUnionRegions(regions);
        List<Region> editableRegions = representableAsUnion ? regions : Collections.emptyList();

        return new BlockSelection(trimmed, editableRegions, expr.isAllBlocks(), expr.runtimeExpr(), expr.geometryExpr());
    }

    private static BlockSelection empty() {
        return new BlockSelection("", Collections.emptyList(), false, SelectionExpr.FALSE, SelectionExpr.FALSE);
    }

        /**
         * Build a selection string from a list of regions.
         */
        public static String toSelectionString(List<Region> regions) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < regions.size(); i++) {
                if (i > 0) {
                    sb.append(" + ");
                }
                Region region = regions.get(i);
                sb.append("(");
                if (region.minX == region.maxX && region.minY == region.maxY && region.minZ == region.maxZ) {
                    sb.append(region.minX).append(",").append(region.minY).append(",").append(region.minZ);
                } else {
                    sb.append(region.minX).append(",").append(region.minY).append(",").append(region.minZ)
                        .append("..").append(region.maxX).append(",").append(region.maxY).append(",").append(region.maxZ);
                }
                sb.append(")");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return this.raw;
        }

        /**
         * An axis-aligned cuboid region (inclusive bounds).
         */
        public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            public boolean contains(int x, int y, int z) {
                return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
            }
        }

        @FunctionalInterface
        private interface SelectionExpr {
            SelectionExpr FALSE = (level, x, y, z) -> false;

            boolean test(ServerLevel level, int x, int y, int z);
        }

        private interface Expr {
            SelectionExpr runtimeExpr();

            SelectionExpr geometryExpr();

            boolean collectUnionRegions(List<Region> regions);

            boolean isAllBlocks();
        }

        private record RegionExpr(Region region) implements Expr {
            @Override
            public SelectionExpr runtimeExpr() {
                return (level, x, y, z) -> region.contains(x, y, z);
            }

            @Override
            public SelectionExpr geometryExpr() {
                return (level, x, y, z) -> region.contains(x, y, z);
            }

            @Override
            public boolean collectUnionRegions(List<Region> regions) {
                regions.add(this.region);
                return true;
            }

            @Override
            public boolean isAllBlocks() {
                return false;
            }
        }

        private record AllExpr() implements Expr {
            @Override
            public SelectionExpr runtimeExpr() {
                return (level, x, y, z) -> true;
            }

            @Override
            public SelectionExpr geometryExpr() {
                return (level, x, y, z) -> true;
            }

            @Override
            public boolean collectUnionRegions(List<Region> regions) {
                return false;
            }

            @Override
            public boolean isAllBlocks() {
                return true;
            }
        }

        private record UnionExpr(Expr left, Expr right) implements Expr {
            @Override
            public SelectionExpr runtimeExpr() {
                SelectionExpr leftExpr = left.runtimeExpr();
                SelectionExpr rightExpr = right.runtimeExpr();
                return (level, x, y, z) -> leftExpr.test(level, x, y, z) || rightExpr.test(level, x, y, z);
            }

            @Override
            public SelectionExpr geometryExpr() {
                SelectionExpr leftExpr = left.geometryExpr();
                SelectionExpr rightExpr = right.geometryExpr();
                return (level, x, y, z) -> leftExpr.test(level, x, y, z) || rightExpr.test(level, x, y, z);
            }

            @Override
            public boolean collectUnionRegions(List<Region> regions) {
                return left.collectUnionRegions(regions) && right.collectUnionRegions(regions);
            }

            @Override
            public boolean isAllBlocks() {
                return left.isAllBlocks() || right.isAllBlocks();
            }
        }

        private record DifferenceExpr(Expr left, Expr right) implements Expr {
            @Override
            public SelectionExpr runtimeExpr() {
                SelectionExpr leftExpr = left.runtimeExpr();
                SelectionExpr rightExpr = right.runtimeExpr();
                return (level, x, y, z) -> leftExpr.test(level, x, y, z) && !rightExpr.test(level, x, y, z);
            }

            @Override
            public SelectionExpr geometryExpr() {
                SelectionExpr leftExpr = left.geometryExpr();
                SelectionExpr rightExpr = right.geometryExpr();
                return (level, x, y, z) -> leftExpr.test(level, x, y, z) && !rightExpr.test(level, x, y, z);
            }

            @Override
            public boolean collectUnionRegions(List<Region> regions) {
                return false;
            }

            @Override
            public boolean isAllBlocks() {
                return false;
            }
        }

        private record FilteredExpr(Expr source, BlockFilter filter) implements Expr {
            @Override
            public SelectionExpr runtimeExpr() {
                SelectionExpr sourceExpr = source.runtimeExpr();
                return (level, x, y, z) -> {
                    if (!sourceExpr.test(level, x, y, z)) {
                        return false;
                    }
                    if (level == null) {
                        return true;
                    }
                    BlockState blockState = level.getBlockState(new BlockPos(x, y, z));
                    return filter.matches(blockState);
                };
            }

            @Override
            public SelectionExpr geometryExpr() {
                return source.geometryExpr();
            }

            @Override
            public boolean collectUnionRegions(List<Region> regions) {
                return false;
            }

            @Override
            public boolean isAllBlocks() {
                return false;
            }
        }

        private static class Parser {

            private final String text;
            private int index = 0;

            private Parser(String text) {
                this.text = text;
            }

            private Expr parseSelection() {
                Expr left = parseFiltered();
                if (left == null) {
                    return null;
                }

                while (true) {
                    skipWhitespace();
                    if (consume('+')) {
                        Expr right = parseFiltered();
                        if (right == null) {
                            return null;
                        }
                        left = new UnionExpr(left, right);
                    } else if (consume('-')) {
                        Expr right = parseFiltered();
                        if (right == null) {
                            return null;
                        }
                        left = new DifferenceExpr(left, right);
                    } else {
                        break;
                    }
                }

                return left;
            }

            private Expr parseFiltered() {
                Expr expr = parsePrimary();
                if (expr == null) {
                    return null;
                }

                while (true) {
                    skipWhitespace();
                    if (!consume('[')) {
                        break;
                    }

                    String filterText = readUntilMatchingBracket();
                    if (filterText == null) {
                        return null;
                    }
                    BlockFilter filter = BlockFilter.parse(filterText);
                    expr = new FilteredExpr(expr, filter);
                }

                return expr;
            }

            private Expr parsePrimary() {
                skipWhitespace();

                if (consume('*')) {
                    return new AllExpr();
                }

                if (consume('(')) {
                    skipWhitespace();
                    int rewind = this.index;

                    Region region = parseRegion();
                    if (region != null) {
                        skipWhitespace();
                        if (consume(')')) {
                            return new RegionExpr(region);
                        }
                    }

                    this.index = rewind;
                    Expr inner = parseSelection();
                    skipWhitespace();
                    if (inner != null && consume(')')) {
                        return inner;
                    }

                    return null;
                }
                return null;
            }

            private Region parseRegion() {
                int rewind = this.index;

                Integer x1 = parseInt();
                if (x1 == null || !consume(',')) {
                    this.index = rewind;
                    return null;
                }
                Integer y1 = parseInt();
                if (y1 == null || !consume(',')) {
                    this.index = rewind;
                    return null;
                }
                Integer z1 = parseInt();
                if (z1 == null) {
                    this.index = rewind;
                    return null;
                }

                if (consumeString("..")) {
                    Integer x2 = parseInt();
                    if (x2 == null || !consume(',')) {
                        this.index = rewind;
                        return null;
                    }
                    Integer y2 = parseInt();
                    if (y2 == null || !consume(',')) {
                        this.index = rewind;
                        return null;
                    }
                    Integer z2 = parseInt();
                    if (z2 == null) {
                        this.index = rewind;
                        return null;
                    }

                    return new Region(
                        Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                        Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
                    );
                }

                return new Region(x1, y1, z1, x1, y1, z1);
            }

            private Integer parseInt() {
                skipWhitespace();

                int start = this.index;
                if (peek('-')) {
                    this.index += 1;
                }

                int digitsStart = this.index;
                while (this.index < this.text.length() && Character.isDigit(this.text.charAt(this.index))) {
                    this.index += 1;
                }

                if (digitsStart == this.index) {
                    this.index = start;
                    return null;
                }

                try {
                    return Integer.parseInt(this.text.substring(start, this.index));
                } catch (NumberFormatException ignored) {
                    this.index = start;
                    return null;
                }
            }

            private String readUntilMatchingBracket() {
                int depth = 0;
                int start = this.index;
                while (this.index < this.text.length()) {
                    char c = this.text.charAt(this.index);
                    if (c == '(') {
                        depth += 1;
                    } else if (c == ')') {
                        depth -= 1;
                        if (depth < 0) {
                            return null;
                        }
                    } else if (c == ']' && depth == 0) {
                        String result = this.text.substring(start, this.index);
                        this.index += 1;
                        return result;
                    }
                    this.index += 1;
                }
                return null;
            }

            private boolean isFullyConsumed() {
                skipWhitespace();
                return this.index == this.text.length();
            }

            private void skipWhitespace() {
                while (this.index < this.text.length() && Character.isWhitespace(this.text.charAt(this.index))) {
                    this.index += 1;
                }
            }

            private boolean consume(char expected) {
                skipWhitespace();
                if (this.index < this.text.length() && this.text.charAt(this.index) == expected) {
                    this.index += 1;
                    return true;
                }
                return false;
            }

            private boolean consumeString(String expected) {
                skipWhitespace();
                if (this.text.startsWith(expected, this.index)) {
                    this.index += expected.length();
                    return true;
                }
                return false;
            }

            private boolean peek(char c) {
                return this.index < this.text.length() && this.text.charAt(this.index) == c;
            }
        }

}
