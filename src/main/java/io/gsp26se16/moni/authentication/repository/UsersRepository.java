package io.gsp26se16.moni.authentication.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.authentication.entity.Users;

@Repository
public interface UsersRepository extends JpaRepository<Users, String> {
    Optional<Users> findByCredential_Id(String credentialId);
}
