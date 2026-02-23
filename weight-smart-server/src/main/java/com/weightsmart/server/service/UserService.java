package com.weightsmart.server.service;

import com.weightsmart.server.model.User;
import com.weightsmart.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

/*
 * UserService
 * Central business logic for Identity and Profile Management.
 * Centralizes user-related business rules:
 * 1. Registration (hash password, default role, save, update search index)
 * 2. Authentication helpers (optional/manual credential checks)
 * 3. Account lockout handling (failed attempts + lock window)
 * 4. Profile updates (safe field updates)
 * 5. Email/password changes
 *
 * Architecture Role:
 * 1. Auth Guard: Validates credentials (Login).
 * 2. Profile Manager: Handles updates to users fields excluding currentWeight and username
 * 3. Registration: Handles ensuring unique username and email for user registration.
 * **CRITICAL:** Acts as the "Event Producer" for the Trie Search Service. When a user is saved,
 * it triggers an update to the in-memory Trie so the user is immediately searchable.
 * 4. Locking: Handles logic for locking accounts on repeated failed logins. 5 failed attempts = 15 minute lockout.
 * 5. Security: Implements RBAC (Role-Based Access Control) logic implicitly by managing the 'role' field.
 *
 * Key Concepts and Documentation
 * <a href="https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html">Spring Dependency Injection</a>:
 * Explains how {@code @Autowired} injects the Repository, Encoder, and TrieService.
 * <a href="https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html">@Transactional Annotation</a>:
 * Documentation on declarative transaction management to ensure data integrity during updates.
 * <a href="https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/password/PasswordEncoder.html">PasswordEncoder Interface</a>:
 * The standard interface for hashing secrets.
 *
 * @author James Chase
 * @version 2.1
 * @since 2026-01-31
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TrieService trieService; // Added Dependency

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TrieService trieService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.trieService = trieService;
    }

    // --- REGISTRATION ---

    /**
     * Registers a new user and updates the Search Index.
     * Uses "Optimistic Write" to handle duplicates (0 lookups).
     *
     * @param user The user entity to save.
     * @return The saved User entity.
     * @throws IllegalArgumentException if username or email is taken.
     */
    public User registerUser(User user) {
        // Hash Password
        // We must hash the password before saving, otherwise it is stored in plain text.
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Set Default Role
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("ROLE_USER");
        }

        try {
            // OPTIMISTIC WRITE: Save to Database
            User savedUser = userRepository.save(user);

            // EVENT HOOK: Update the In-Memory Trie
            // This ensures the new user appears in search results immediately.
            trieService.insert(savedUser.getUsername());

            return savedUser;

        } catch (DataIntegrityViolationException e) {
            // CATCH THE COLLISION
            // This happens if Username OR Email is taken.
            throw new IllegalArgumentException("Registration failed. Please check your details.");
        }
    }

    // ---  PROFILE UPDATES ---

    /**
     * Updates non-sensitive profile fields (age, height, goalWeight, targetDate, nickname).
     * Only non-null fields in the updates object are applied (partial update pattern).
     *
     * @param userId The ID of the user to update.
     * @param updates A User object containing only the fields to modify (null = skip).
     * @return The updated User entity after save.
     * @throws IllegalArgumentException if user not found.
     */
    @Transactional // Ensures database integrity (Atomic operation)
    public User updateProfile(Long userId, User updates) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (updates.getAge() > 0) existingUser.setAge(updates.getAge());
        if (updates.getHeight() != null) existingUser.setHeight(updates.getHeight());
        if (updates.getGoalWeight() != null) existingUser.setGoalWeight(updates.getGoalWeight());
        if (updates.getTargetDate() != null) existingUser.setTargetDate(updates.getTargetDate());
        if (updates.getNickname() != null) {
            existingUser.setNickname(updates.getNickname().trim());
        }
        return userRepository.save(existingUser);
    }

    // --- DATA RETRIEVAL ---

    /**
     * Retrieves a user by their unique username.
     * Used by AuthController after authentication to load the full profile.
     *
     * @param username The unique login identifier.
     * @return An Optional containing the User if found, or empty if not.
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

}