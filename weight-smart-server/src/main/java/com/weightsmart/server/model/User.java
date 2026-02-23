package com.weightsmart.server.model;

import jakarta.persistence.*; //JPA tolls (Entity, ID, Column, etc) Java to SQL Mapper
import jakarta.validation.constraints.Email; //Email validation
import jakarta.validation.constraints.NotBlank; //enforces non empty values
import jakarta.validation.constraints.Pattern; //Enforces valid characters for username
import jakarta.validation.constraints.Size; //Enforces size range for username
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

/*
 * User Entity
 * Represents a registered user in the WeightSmart system.
 *
 * Architecture Role:
 * It enforces unique constraints on identity fields (username, email) at the database level,
 * ensuring data integrity before logic even runs.  Applies RBAC and security extensions.
 *
 * Key Concepts & Documentation:
 * B-Tree Indexing: The {@code unique=true} attribute automatically creates B-Tree indexes
 * for O(log n) lookup performance. <a href="https://www.postgresql.org/docs/current/sql-createindex.html">Reference: PostgreSQL Indexes</a>
 * BCrypt Hashing: Passwords are never stored in plaintext. They are hashed using the BCrypt algorithm.
 * <a href="https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/bcrypt/BCrypt.html">Reference: BCrypt Algorithm</a>
 * Lombok: Used for generation of getter, setter, toString, Builder, NoArgsConstructor, AllArgsConstructor methods
 * <a href="https://projectlombok.org/features/.html">Reference: Lombok Documentation</a>
 * Jarkarta Persistence: Provides access to Entity , Table, and ManytoOne. Provides of java objects to records into PostgreSQL
 * <a href="https://jakarta.ee/specifications/persistence/3.0/apidocs/jakarta.persistence/jakarta/persistence/package-summary.html"> Reference= Jakarta Persistence Documentation</a>
 * Jakarta Validation: Provides validation methods @Email, @NotBlank, @Pattern, @Size for simplified data attribute validation
 * <a href="https://beanvalidation.org/2.0/spec/.html">Jakarta Bean Validation 2.0 Documentation</a>
 * Role-Based Access Control (RBAC):  Role Field distinguishes ROLE_USER vs ROLE_ADMIN
 * Locking:  failedLoginInAttempts applies counter to attempted login. In combination with lockTime, accounts are locked after 5 subsequent failed logins to prevent brute-force attacks
 * UserDetails: Implements Spring Security's core interface to bridge our custom DB with the authentication framework.
 * <a href="https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/user-details.html">Reference: Spring Security UserDetails</a>
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */


@Entity  //Hibernate: Class to database table (generates SQL)
/*
 * @Table name of database is users.
 * @Indexes: Production hash index for O(1) look-up
 */
@Table(name = "users", indexes =  {
        @Index(name = "idx_users_username", columnList = "username")
})
@Data //Lombok: Generates Getters, Setters, toString(), equals(), and hashCode()
@NoArgsConstructor //Lombok: Generates blank constructor public User() {}
@AllArgsConstructor //Lombok: Generates constructor with all arguments passed
@Builder //Lombok: Allows creation of objects with any number of args passed. Must define required attributes
public class User implements UserDetails {

    /**
     * Primary Key of table User made to _id
     * id generated via auto-incrementation on entity creation
     * id : private Long wrapper allows field to be null before insertion
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Column: Defines attribute username
     * unique = true: No two users can have same username
     * nullable = false: This field cannot be null
     * length = 30: Sets maximum username length to 30 char, trie exploitation defense
     * NotBlanK: Validates when controller receives data, checks against empty, white space, and null
     */
    @NotBlank(message = "Username cannot be empty")
    @Size(min=3, max =30, message = "Username must be 3-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    @Column(unique = true, nullable = false, length = 30)
    private String username;

    /**
     * Column: Defines attribute password
     * nullable = false: This field cannot be null
     * Stored as salt-hash string using BCrypt
     * NotBlanK: Validates when controller receives data, checks against empty, white space, and null
     */
    @NotBlank(message = "Password is required.")
    @Column(nullable = false)
    private String password;


    /**
     * Column: Defines attribute email
     * unique = true: No two users can have same username
     * Creates B-Tree over users on email, on registration email verification is O(logN)
     * nullable = false: This field cannot be null
     * NotBlanK: Validates when controller receives data, checks against empty, white space, and null
     * Email: Prevents garbage data like "bob@@gmail" from entering DB.
     */
    @NotBlank(message = "Email cannot be empty")
    @Column(unique = true, nullable = false)
    @Email(message = "Must be a valid email address")
    private String email;

    //FIXME: Add user phone-number or use in app notifications

    /**
     * Column: Defines optional attribute Nickname / Display Name.
     * Allows users to show a friendly name instead of their login username.
     */
    @Size(max = 20, message = "Nickname must be 20 characters or less")
    @Pattern(regexp = "^[A-Za-z]+$", message = "Nickname can only Can youi contain letters.")
    @Column(length = 20)
    private String nickname;

    //FIXME: Add user avatar field

    //nullable field age as an integer
    private int age;

    //nullable field height as an integer (for BMI Calculation)
    private Double height;

    //nullable double current weight
    private Double currentWeight;

    //nullable double goal weight
    private Double goalWeight;

    // nullable date type field for target date for goal weight
    private LocalDate targetDate;

    // --- RBAC and SECURITY ---
    /**
     * Role-Based Access Control
     * Defaults to making new users have ROLE_USER
     * Values: "Role_USER","Role_Admin
     * default: "ROLE_USER"
     */
    @Column(nullable = false)
    private String role = "ROLE_USER";

    /**
     * Account Locking: Tracks consecutive failed login attempts
     * At five failed logins triggers isAccountNonLocked to False
     * At successful login reset to zero.
     * At successful unlock set to zero.
     */
    @Column(name = "failed_attempts")
    private int failedLoginAttempts =0;

    /**
     * Account Locking: Timestamp for when the account unlocks
     * Set 15 minutes from isAccountNonLocked set to False
     * Set to null when isAccountNonLocked is True
     */
    @Column(name = "lock_time")
    private LocalDateTime lockTime;

    /**
     * Optimistic Locking: Prevents concurrent profile updates from silently overwriting each other.
     * Hibernate auto-increments on every UPDATE and includes WHERE version = ? in the SQL.
     */
    @Version
    @Column(columnDefinition = "integer default 0")
    private Integer version = 0;

    // --- User Deletion --- FIXME: Implementation of user account deletion required in future sprints
    /**
     * Soft Delete / Activation Flag.
     * If false, user cannot log in.
     * Stages user accounts for hard deletion in compliance with Right to Forgotten in 30 days
     */
    @Column(name = "is_enabled")
    private boolean isEnabled = true;

    // --- USERDETAILS IMPLEMENTATION (Required for Spring Security) ---

    /**
     * Wrap role type string into SimpleGrantedAuthority Object
     * Used in combination with .requestMatchers("/role").hasRole("Role") to determine
     * if user request is to be blocked or not
     * @return user as wrap object with role exposed
     */
    @NonNull
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Converts our String "ROLE_USER" into a Security Authority object
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    /**
     * Boolean Boiler plate to hold database column for further implementation
     * @return true always for now
     */
    @Override
    public boolean isAccountNonExpired() { return true; }

    /**
     * Boolean check for if the account is unlocked
     * @return True if account is not locked, returns the evaluation of locktime before now. Should evaluate as false another logic error
     */
    @Override
    public boolean isAccountNonLocked() {
        // Account is locked if lockTime is currently in the future
        if (lockTime == null) return true;
        return lockTime.isBefore(LocalDateTime.now());
    }

    /**
     * Boolean Boiler plate to hold database column for further implementation
     * @return true always for now
     */
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /**
     * Is account enabled or disabled?
     * Returns as true unless soft deletion has occured
     * //FIXME: Logic for implementation to be added when deletion logic is completed in future sprints. Until then always true
     * @return
     */
    @Override
    public boolean isEnabled() { return isEnabled; }

}
