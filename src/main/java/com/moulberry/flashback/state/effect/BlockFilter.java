package com.moulberry.flashback.state.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Parsed block filter expression used by block selections and replace effects.
 */
public class BlockFilter {

    private static final Node TRUE_NODE = state -> true;
    private static final Node FALSE_NODE = state -> false;

    private final String raw;
    private final Node root;

    private BlockFilter(String raw, Node root) {
        this.raw = raw;
        this.root = root;
    }

    public String raw() {
        return this.raw;
    }

    public boolean matches(BlockState state) {
        return this.root.matches(state);
    }

    public static BlockFilter parse(String text) {
        if (text == null || text.isBlank()) {
            return new BlockFilter("", TRUE_NODE);
        }

        Parser parser = new Parser(text.trim());
        Node node = parser.parse();
        if (node == null) {
            return new BlockFilter(text, FALSE_NODE);
        }
        return new BlockFilter(text, node);
    }

    private interface Node {
        boolean matches(BlockState state);
    }

    private static class Parser {

        private final String text;
        private int index = 0;

        private Parser(String text) {
            this.text = text;
        }

        private Node parse() {
            Node node = parseOr();
            skipWhitespace();
            if (node == null || index != text.length()) {
                return null;
            }
            return node;
        }

        private Node parseOr() {
            Node left = parseAnd();
            if (left == null) {
                return null;
            }

            while (true) {
                skipWhitespace();
                if (!consume('|')) {
                    break;
                }

                Node right = parseAnd();
                if (right == null) {
                    return null;
                }

                left = new OrNode(left, right);
            }

            return left;
        }

        private Node parseAnd() {
            Node left = parseUnary();
            if (left == null) {
                return null;
            }

            while (true) {
                skipWhitespace();
                if (!(consume(',') || consume('&'))) {
                    break;
                }

                Node right = parseUnary();
                if (right == null) {
                    return null;
                }

                left = new AndNode(left, right);
            }

            return left;
        }

        private Node parseUnary() {
            skipWhitespace();
            if (consume('!')) {
                Node inner = parseUnary();
                if (inner == null) {
                    return null;
                }
                return new NotNode(inner);
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            skipWhitespace();

            if (consume('(')) {
                Node node = parseOr();
                skipWhitespace();
                if (node == null || !consume(')')) {
                    return null;
                }
                return node;
            }

            if (consume('#')) {
                String token = readToken();
                if (token.isEmpty()) {
                    return null;
                }
                return createTagNode(token);
            }

            String token = readToken();
            if (token.isEmpty()) {
                return null;
            }
            return createBlockNode(token);
        }

        private Node createTagNode(String token) {
            ResourceLocation id;
            try {
                id = parseResourceLocation(token);
            } catch (Exception ignored) {
                return FALSE_NODE;
            }

            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, id);
            return state -> state.is(tagKey);
        }

        private Node createBlockNode(String token) {
            ResourceLocation id;
            try {
                id = parseResourceLocation(token);
            } catch (Exception ignored) {
                return FALSE_NODE;
            }

            Block block = BuiltInRegistries.BLOCK.getValue(id);
            if (block == null) {
                return FALSE_NODE;
            }

            return state -> state.is(block);
        }

        private static ResourceLocation parseResourceLocation(String token) {
            if (token.indexOf(':') >= 0) {
                return ResourceLocation.parse(token);
            }
            return ResourceLocation.withDefaultNamespace(token);
        }

        private String readToken() {
            skipWhitespace();
            int start = this.index;
            while (this.index < this.text.length()) {
                char c = this.text.charAt(this.index);
                if (Character.isWhitespace(c) || c == '|' || c == '&' || c == ',' || c == '!' || c == '(' || c == ')') {
                    break;
                }
                this.index += 1;
            }
            if (start == this.index) {
                return "";
            }
            return this.text.substring(start, this.index);
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index += 1;
            }
        }

        private boolean consume(char c) {
            if (index < text.length() && text.charAt(index) == c) {
                index += 1;
                return true;
            }
            return false;
        }
    }

    private record NotNode(Node inner) implements Node {
        @Override
        public boolean matches(BlockState state) {
            return !inner.matches(state);
        }
    }

    private record AndNode(Node left, Node right) implements Node {
        @Override
        public boolean matches(BlockState state) {
            return left.matches(state) && right.matches(state);
        }
    }

    private record OrNode(Node left, Node right) implements Node {
        @Override
        public boolean matches(BlockState state) {
            return left.matches(state) || right.matches(state);
        }
    }
}
