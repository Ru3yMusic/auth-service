package com.rubymusic.auth.repository;

import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /** Admin user list — filterable by search query and/or status */
    @Query("SELECT u FROM User u WHERE " +
           "(:q IS NULL OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "ORDER BY u.createdAt DESC")
    Page<User> findByFilters(@Param("q") String q,
                             @Param("status") UserStatus status,
                             Pageable pageable);

    /** For dashboard: count users per gender */
    @Query("SELECT u.gender, COUNT(u) FROM User u GROUP BY u.gender")
    List<Object[]> countByGender();

    /** For dashboard: count users per status */
    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    List<Object[]> countByStatus();

    /** Recently registered users for the dashboard widget */
    List<User> findTop10ByOrderByCreatedAtDesc();
}
