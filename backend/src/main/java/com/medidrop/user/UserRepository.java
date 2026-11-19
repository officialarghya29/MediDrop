package com.medidrop.user;

import com.medidrop.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where (:q is null or lower(u.fullName) like lower(concat('%', :q, '%'))
                or lower(u.email) like lower(concat('%', :q, '%'))
                or u.phone like concat('%', :q, '%'))
            and (:role is null or u.role = :role)
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);
}
