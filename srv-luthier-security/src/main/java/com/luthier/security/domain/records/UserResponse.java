package com.luthier.security.domain.records;

import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.domain.enums.Profile;
import com.luthier.shared.validation.Cpf;
import java.util.UUID;

/** CPF sempre mascarado (LGPD). */
public record UserResponse(UUID id, UUID tenantId, String name, String email, String maskedCpf,
                           String phone, Profile profile, boolean active) {

  public static UserResponse from(UserEntity user) {
    return new UserResponse(user.getUuid(), user.getTenantId(), user.getName(), user.getEmail(),
        Cpf.mask(user.getCpf()), user.getPhone(), user.getProfile(), user.isActive());
  }
}
