package com.luthier.services.impl;

import com.luthier.domain.entities.UserEntity;
import com.luthier.domain.enums.Profile;
import com.luthier.domain.records.CreateUserRequest;
import com.luthier.domain.records.UserResponse;
import com.luthier.domain.repositories.RefreshTokenRepository;
import com.luthier.domain.repositories.UserRepository;
import com.luthier.errors.ConflictException;
import com.luthier.errors.NotFoundException;
import com.luthier.services.UserManagementService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;   // NOVO
  private final PasswordEncoder passwordEncoder;

  public UserManagementServiceImpl(UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserResponse create(CreateUserRequest request) {
    var email = request.email().strip();

    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("Email already exists");
    }

    var user = userRepository.save(
        UserEntity.newUser(email, passwordEncoder.encode(request.password()), request.profile())
    );

    return UserResponse.from(user);
  }

  @Override
  public UserResponse changeProfile(UUID id, Profile profile) {
    var user = find(id);
    user.changeProfile(profile);
    // O JWT atual (até 15 min) ainda carrega o papel antigo; revogar força um refresh e o novo
    // access token já sai com o papel atualizado.
    refreshTokenRepository.revokeAllByUser(user.getUuid(), Instant.now());
    return UserResponse.from(user);
  }

  @Override
  public UserResponse activate(UUID id) {
    var user = find(id);
    user.activate();
    return UserResponse.from(user);
  }

  @Override
  public UserResponse deactivate(UUID id) {
    var user = find(id);
    user.deactivate();
    // Usuário desativado não pode mais renovar sessão (o JWT em mãos expira em no máx. 15 min).
    refreshTokenRepository.revokeAllByUser(user.getUuid(), Instant.now());
    return UserResponse.from(user);
  }

  private UserEntity find(UUID id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("User not found!"));
  }
}
