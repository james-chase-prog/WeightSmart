package com.weightsmart.server.repository;

import com.weightsmart.server.model.User;
import org.springframework.data.jpa.repository.JpaRepository; //Parent class containing CRUD and other methods
import org.springframework.stereotype.Repository; //Spring framework import

import java.util.Optional; //Box wrapper for null handling

/*
 * UserRepository
 * Handles Data Access Object (DAO) operations for User entities. Spring Data Repository that provides CRUD and derived
 * queries for User.  Accessed by UserService and UserDetailsService (Application Config)
 *
 * Architecture Role:
 * Abstraction layer between the {@code UserService} and the PostgreSQL database.
 * It leverages Spring Data JPA to generate SQL automatically from method names.
 *
 * Key Concepts & Documentation:
 * <a href="https://docs.spring.io/spring-data/jpa/docs/current/api/org/springframework/data/jpa/repository/JpaRepository.html">Spring Data JpaRepository</a>:
 * Explains the available CRUD methods (save, findById, delete).
 * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html">Java Optional Class</a>:
 * Best practices for handling potential null returns (e.g., user not found).
 *
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */

/**
 * Repository interface for User entity operations
 * Extends JpaRepository for CRUD Operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Retrieves a User entity by unique username
     * Using Optional prevents NullPointerExceptions if user does not exist
     * Optional requires unpacking after access
     *
     * @param username unique search object
     * @return Optional containing user if found or empty if not
     *
     * Generated SQL: SELECT * FROM users WHERE username = arg
     */
    Optional<User> findByUsername(String username);

}