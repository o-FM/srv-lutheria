package com.luthier.security.services;

import com.luthier.shared.authorization.Access;
import com.luthier.security.domain.enums.Profile;
import com.luthier.security.domain.records.CreateUserRequest;
import com.luthier.security.domain.records.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Gestão de usuários SEMPRE dentro da luthieria (tenant) de quem está logado. */
public interface UserManagementService {

  @PreAuthorize(Access.ADMIN)
  UserResponse create(CreateUserRequest request);

  @PreAuthorize(Access.ADMIN)
  List<UserResponse> list();

  @PreAuthorize(Access.ANY_STAFF)
  UserResponse me(UUID userId);

  @PreAuthorize(Access.ADMIN)
  UserResponse changeProfile(UUID id, Profile profile);

  @PreAuthorize(Access.ADMIN)
  UserResponse activate(UUID id);

  @PreAuthorize(Access.ADMIN)
  UserResponse deactivate(UUID id);
}
