package com.luthier.domain.records;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import com.luthier.validation.Cpf;
import java.util.UUID;

/** CPF sempre mascarado (LGPD). */
public record UserResponse(UUID id, UUID tenantId, String name, String email, String maskedCpf,
                           String phone, Profile profile, boolean active) {

  public static UserResponse from(UserEntity user) {
    return new UserResponse(user.getUuid(), user.getTenantId(), user.getName(), user.getEmail(),
        Cpf.mask(user.getCpf()), user.getPhone(), user.getProfile(), user.isActive());
  }
}
