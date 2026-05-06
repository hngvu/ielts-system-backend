package io.gsp26se16.moni.authentication.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.UserCredentials;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, String> {
    boolean existsByEmail(String email);

    Optional<UserCredentials> findByEmail(String email);

    Optional<UserCredentials> findByUser_Id(String userId);

    List<UserCredentials> findByRole(UserCredentials.Role role);
}
