package com.luthier.domain.repositories;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);
  boolean existsByProfile(Profile profile);
}
