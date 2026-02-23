/*
 * Custom indexes that require PostgreSQL-specific syntax beyond JPA's capabilities.
 * Runs after Hibernate DDL via spring.jpa.defer-datasource-initialization=true.
 *
 * Hash index on username: All username queries are equality-only (login, JWT validation,
 * profile lookup), making Hash O(1) the optimal choice over B-tree O(log n).
 * The UNIQUE constraint on the column still maintains its own B-tree for enforcement.
 */
CREATE INDEX IF NOT EXISTS idx_users_username ON users USING hash (username);
