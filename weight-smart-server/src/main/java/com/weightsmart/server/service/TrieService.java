package com.weightsmart.server.service;

import com.weightsmart.server.model.User;
import com.weightsmart.server.repository.UserRepository;
import com.weightsmart.server.util.TrieNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * TrieService
 * An in-memory search engine for usernames.
 *
 * Architecture Role:
 * 1. Immediate Availability: Uses @PostConstruct to load all users into RAM during server boot.
 * 2. Sparse Generation: Builds the tree path-by-path. If "test" is the only user,
 * the tree structure is effectively a linked list: Root -> T -> E -> S -> T.
 * 3. Memory Safety: Supports deletion and scheduled rebuilds to prevent memory leaks.
 *
 * Key Concepts and Documentation:
 * <a href="https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html">@PostConstruct</a>:
 * Guarantees this runs once dependency injection is complete, ensuring the Trie is ready
 * before the server accepts its first HTTP request.
 *
 * @author James Chase
 * @version 2.2
 * @since 2026-01-31
 */
@Service
public class TrieService {

    private static final Logger log = LoggerFactory.getLogger(TrieService.class);
    private static final int MAX_SEARCH_RESULTS = 10;

    private volatile TrieNode root = new TrieNode();
    private final UserRepository userRepository;

    @Autowired
    public TrieService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Eager Initialization (Cache Warming).
     * Fetches all users and builds the sparse tree immediately on startup.
     */
    @PostConstruct
    public void init() {
        rebuildTrie();
    }

    /**
     * Rebuilds the entire Trie from the database.
     * Called on startup and periodically as a safety net for consistency.
     */
    private synchronized void rebuildTrie() {
        log.info("TrieService: Building Sparse Trie from Database...");
        long startTime = System.currentTimeMillis();

        // Create new root to avoid concurrent modification issues
        TrieNode newRoot = new TrieNode();
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            insertInto(newRoot, user.getUsername());
        }

        // Atomic swap
        this.root = newRoot;

        long duration = System.currentTimeMillis() - startTime;
        log.info("TrieService: Build Complete. Indexed {} users in {}ms.", allUsers.size(), duration);
    }

    /**
     * Scheduled rebuild to ensure consistency with database.
     * Runs daily at 3:00 AM as a safety net for any missed deletions.
     */
    @Scheduled(cron = "0 0 3 * * *") // 3:00 AM daily
    public void scheduledRebuild() {
        log.info("TrieService: Starting scheduled rebuild...");
        rebuildTrie();
    }

    /**
     * Inserts a username into the Trie.
     * This logic ensures nodes are "only created when there is a character from a username".
     */
    public void insert(String username) {
        insertInto(root, username);
    }

    /**
     * Internal insert method that works on a specific root node.
     * Used by both public insert() and rebuildTrie().
     */
    private void insertInto(TrieNode targetRoot, String username) {
        if (username == null || username.isEmpty()) return;

        TrieNode current = targetRoot;
        String normalized = username.toLowerCase();

        for (char c : normalized.toCharArray()) {
            // computeIfAbsent:
            // "If the node for 't' exists, return it.
            //  If NOT, create a NEW node and put it in the map."
            // This guarantees no unused nodes
            current = current.getChildren().computeIfAbsent(c, k -> new TrieNode());
        }
        current.setEndOfWord(true);
        current.setOriginalUsername(username);
    }

    /**
     * Removes a username from the Trie.
     * Called when a user is deleted to prevent stale data in search results.
     *
     * @param username The username to remove.
     * @return true if the username was found and removed, false otherwise.
     */
    public boolean delete(String username) {
        if (username == null || username.isEmpty()) return false;

        String normalized = username.toLowerCase();
        return deleteRecursive(root, normalized, 0);
    }

    /**
     * Recursively deletes a username and prunes empty branches.
     */
    private boolean deleteRecursive(TrieNode current, String username, int index) {
        if (index == username.length()) {
            // Reached the end of the username
            if (!current.isEndOfWord()) {
                return false; // Username not found
            }
            current.setEndOfWord(false);
            current.setOriginalUsername(null);
            // Return true if this node has no children (can be pruned)
            return current.getChildren().isEmpty();
        }

        char c = username.charAt(index);
        TrieNode child = current.getChildren().get(c);

        if (child == null) {
            return false; // Username not found
        }

        boolean shouldDeleteChild = deleteRecursive(child, username, index + 1);

        if (shouldDeleteChild) {
            current.getChildren().remove(c);
            // Return true if current node can also be pruned
            return current.getChildren().isEmpty() && !current.isEndOfWord();
        }

        return false;
    }

    /**
     * Searches for usernames matching the given prefix.
     */
    public List<String> search(String prefix) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return results;

        TrieNode current = root;
        String normalized = prefix.toLowerCase();

        // Navigate existing nodes
        for (char c : normalized.toCharArray()) {
            TrieNode node = current.getChildren().get(c);
            if (node == null) {
                // Path doesn't exist -> Prefix doesn't exist
                return results;
            }
            current = node;
        }

        // Collect descendants
        findAllWords(current, results);
        return results;
    }

    private void findAllWords(TrieNode node, List<String> results) {
        if (results.size() >= MAX_SEARCH_RESULTS) return;

        if (node.isEndOfWord()) {
            results.add(node.getOriginalUsername());
        }

        for (Map.Entry<Character, TrieNode> entry : node.getChildren().entrySet()) {
            findAllWords(entry.getValue(), results);
        }
    }
}