package com.moulberry.flashback.state.effect;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a selection of blocks in the world.
 * Syntax:
 * - {@code <x1,y1,z1>-<x2,y2,z2>} : A cuboid region
 * - {@code <x,y,z>} : A single block
 * - {@code selection1:selection2:...} : A union of multiple regions
 */
public class BlockSelection {

    private final String raw;
    private final List<Region> regions;

    public BlockSelection(String raw, List<Region> regions) {
        this.raw = raw;
        this.regions = Collections.unmodifiableList(regions);
    }

    public String getRaw() {
        return this.raw;
    }

    public List<Region> getRegions() {
        return this.regions;
    }

    /**
     * Check if a block position is contained within this selection.
     */
    public boolean contains(BlockPos pos) {
        for (Region region : this.regions) {
            if (region.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(int x, int y, int z) {
        for (Region region : this.regions) {
            if (region.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if this selection is empty (no regions).
     */
    public boolean isEmpty() {
        return this.regions.isEmpty();
    }

    /**
     * Parse a selection string into a BlockSelection.
     * Returns an empty selection if parsing fails.
     */
    public static BlockSelection parse(String text) {
        if (text == null || text.isBlank()) {
            return new BlockSelection("", Collections.emptyList());
        }

        text = text.trim();
        List<Region> regions = new ArrayList<>();
        String[] parts = text.split(":");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            Region region = parseRegion(part);
            if (region != null) {
                regions.add(region);
            }
        }

        return new BlockSelection(text, regions);
    }

    private static Region parseRegion(String part) {
        // Try cuboid: <x1,y1,z1>-<x2,y2,z2>
        int dashIndex = findDashBetweenBrackets(part);
        if (dashIndex >= 0) {
            String left = part.substring(0, dashIndex).trim();
            String right = part.substring(dashIndex + 1).trim();
            int[] p1 = parseCoord(left);
            int[] p2 = parseCoord(right);
            if (p1 != null && p2 != null) {
                return new Region(
                    Math.min(p1[0], p2[0]), Math.min(p1[1], p2[1]), Math.min(p1[2], p2[2]),
                    Math.max(p1[0], p2[0]), Math.max(p1[1], p2[1]), Math.max(p1[2], p2[2])
                );
            }
        }

        // Try single block: <x,y,z>
        int[] coords = parseCoord(part);
        if (coords != null) {
            return new Region(coords[0], coords[1], coords[2], coords[0], coords[1], coords[2]);
        }

        return null;
    }

    /**
     * Find the dash '-' that separates two bracket groups.
     * e.g. {@code <1,2,3>-<4,5,6>} → returns index of '-'.
     */
    private static int findDashBetweenBrackets(String s) {
        int bracketDepth = 0;
        boolean seenFirstBracket = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                bracketDepth++;
                seenFirstBracket = true;
            } else if (c == '>') {
                bracketDepth--;
            } else if (c == '-' && bracketDepth == 0 && seenFirstBracket) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parse a coordinate string like {@code <x,y,z>} into an int[3].
     */
    private static int[] parseCoord(String s) {
        s = s.trim();
        if (!s.startsWith("<") || !s.endsWith(">")) {
            return null;
        }
        s = s.substring(1, s.length() - 1);
        String[] parts = s.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Build a selection string from a list of regions.
     */
    public static String toSelectionString(List<Region> regions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < regions.size(); i++) {
            if (i > 0) sb.append(":");
            Region r = regions.get(i);
            if (r.minX == r.maxX && r.minY == r.maxY && r.minZ == r.maxZ) {
                sb.append("<").append(r.minX).append(",").append(r.minY).append(",").append(r.minZ).append(">");
            } else {
                sb.append("<").append(r.minX).append(",").append(r.minY).append(",").append(r.minZ).append(">");
                sb.append("-");
                sb.append("<").append(r.maxX).append(",").append(r.maxY).append(",").append(r.maxZ).append(">");
            }
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
}
