package com.luthier.domain.records;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import java.util.UUID;

public record UserResponse(UUID id, String email, Profile profile, boolean active) {

  public static UserResponse from(UserEntity user) {
    return new UserResponse(user.getUuid(), user.getEmail(), user.getProfile(), user.isActive());
  }
}