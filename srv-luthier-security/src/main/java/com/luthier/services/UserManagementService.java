package com.luthier.services;

import com.luthier.authorization.Access;
import com.luthier.domain.enums.Profile;
import com.luthier.domain.records.CreateUserRequest;
import com.luthier.domain.records.UserResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public interface UserManagementService {

  @PreAuthorize(Access.ADMIN)
  UserResponse create(CreateUserRequest request);

  @PreAuthorize(Access.ADMIN)
  UserResponse changeProfile(UUID id, Profile profile);

  @PreAuthorize(Access.ADMIN)
  UserResponse activate(UUID id);

  @PreAuthorize(Access.ADMIN)
  UserResponse deactivate(UUID id);
}
