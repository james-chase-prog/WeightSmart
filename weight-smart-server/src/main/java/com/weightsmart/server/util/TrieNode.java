package com.weightsmart.server.util;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/*
 * TrieNode
 * A "Sparse" Prefix Tree node.
 *
 * Architecture Role:
 * 1. Memory Efficiency: Uses a HashMap for children.
 * If the Trie contains only "test", the root node will have exactly ONE entry in its map ('t').
 * It does NOT allocate space for 'a', 'b', 'c', etc.
 * 2. Case Preservation: Stores the original string at the leaf for display purposes.
 *
 * Key Concepts:
 * Sparse Allocation: Nodes are created only when a character matches.
 * Lombok: @Getter/@Setter annotations reduce boilerplate code.
 *
 * @author James Chase
 * @version 2.1
 * @since 2026-01-31
 */
@Getter
@Setter
public class TrieNode {

    // Using HashMap ensures we only create nodes that actually exist.
    // Lombok will generate getChildren(), but NO SETTER (because it is final).
    private final Map<Character, TrieNode> children = new HashMap<>();

    // Lombok generates: boolean isEndOfWord(); void setEndOfWord(boolean val);
    private boolean isEndOfWord = false;

    // Lombok generates: String getOriginalUsername(); void setOriginalUsername(String val);
    private String originalUsername = null;
}