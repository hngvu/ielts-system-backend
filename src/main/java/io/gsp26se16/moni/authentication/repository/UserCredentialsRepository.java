package io.gsp26se16.moni.authentication.repository;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, String> {
    boolean existsByEmail(String email);

    Optional<UserCredentials> findByEmail(String email);
}
