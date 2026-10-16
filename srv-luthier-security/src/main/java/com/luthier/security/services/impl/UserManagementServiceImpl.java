package com.luthier.security.services.impl;

import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.domain.enums.Profile;
import com.luthier.security.domain.records.CreateUserRequest;
import com.luthier.security.domain.records.UserResponse;
import com.luthier.security.domain.repositories.RefreshTokenRepository;
import com.luthier.security.domain.repositories.UserRepository;
import com.luthier.shared.errors.ConflictException;
import com.luthier.shared.errors.NotFoundException;
import com.luthier.security.services.UserManagementService;
import com.luthier.shared.audit.AuditEvent;
import com.luthier.shared.tenancy.TenantContext;
import com.luthier.shared.validation.Cpf;
import com.luthier.shared.validation.Phone;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;

  public UserManagementServiceImpl(UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.events = events;
  }

  @Override
  public UserResponse create(CreateUserRequest request) {
    var tenantId = TenantContext.require();
    var email = request.email().strip().toLowerCase(Locale.ROOT);
    var cpf = Cpf.digits(request.cpf());

    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("Email already exists");
    }
    if (cpf != null && userRepository.existsByTenantIdAndCpf(tenantId, cpf)) {
      throw new ConflictException("CPF already exists");
    }

    var user = userRepository.save(UserEntity.newUser(
        tenantId,
        request.name().strip(),
        email,
        cpf,
        Phone.digits(request.phone()),
        passwordEncoder.encode(request.password()),
        request.profile()));

    events.publishEvent(AuditEvent.of("USER_CREATED", "user", user.getUuid(),
        "perfil " + user.getProfile().getValue()));
    return UserResponse.from(user);
  }

  @Override
  public List<UserResponse> list() {
    return userRepository.findAllByTenantIdOrderByNameAsc(TenantContext.require()).stream()
        .map(UserResponse::from)
        .toList();
  }

  @Override
  public UserResponse me(UUID userId) {
    return UserResponse.from(find(userId));
  }

  @Override
  public UserResponse changeProfile(UUID id, Profile profile) {
    var user = find(id);
    user.changeProfile(profile);
    // O JWT atual (até 15 min) ainda carrega o papel antigo; revogar força um refresh e o novo
    // access token já sai com o papel atualizado.
    refreshTokenRepository.revokeAllByUser(user.getUuid(), Instant.now());
    events.publishEvent(AuditEvent.of("USER_PROFILE_CHANGED", "user", id,
        "novo perfil " + profile.getValue()));
    return UserResponse.from(user);
  }

  @Override
  public UserResponse activate(UUID id) {
    var user = find(id);
    user.activate();
    events.publishEvent(AuditEvent.of("USER_ACTIVATED", "user", id, null));
    return UserResponse.from(user);
  }

  @Override
  public UserResponse deactivate(UUID id) {
    var user = find(id);
    user.deactivate();
    // Usuário desativado não pode mais renovar sessão (o JWT em mãos expira em no máx. 15 min).
    refreshTokenRepository.revokeAllByUser(user.getUuid(), Instant.now());
    events.publishEvent(AuditEvent.of("USER_DEACTIVATED", "user", id, null));
    return UserResponse.from(user);
  }

  /** USER_TB não é filtrada pelo Hibernate: o filtro por tenant aqui é obrigatório. */
  private UserEntity find(UUID id) {
    return userRepository.findByUuidAndTenantId(id, TenantContext.require())
        .orElseThrow(() -> new NotFoundException("User not found!"));
  }
}
