package com.luthier.security.services.impl;

import com.luthier.security.domain.entities.TenantEntity;
import com.luthier.security.domain.entities.UserEntity;
import com.luthier.security.domain.enums.Profile;
import com.luthier.security.TenantRegistered;
import com.luthier.security.domain.records.RegisterTenantRequest;
import com.luthier.security.domain.records.TokenResponse;
import com.luthier.security.domain.repositories.TenantRepository;
import com.luthier.security.domain.repositories.UserRepository;
import com.luthier.shared.errors.ConflictException;
import com.luthier.security.services.AuthService;
import com.luthier.security.services.TenantRegistrationService;
import com.luthier.shared.validation.Cpf;
import com.luthier.shared.validation.Phone;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantRegistrationServiceImpl implements TenantRegistrationService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final ApplicationEventPublisher events;

  public TenantRegistrationServiceImpl(TenantRepository tenantRepository,
      UserRepository userRepository, PasswordEncoder passwordEncoder, AuthService authService,
      ApplicationEventPublisher events) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authService = authService;
    this.events = events;
  }

  @Override
  @Transactional
  public TokenResponse register(RegisterTenantRequest request) {
    var owner = request.owner();
    var email = owner.email().strip().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("Email already exists");
    }

    var workshop = request.workshop();
    var name = workshop.name().strip();
    var tenant = tenantRepository.save(
        TenantEntity.newTenant(name, uniqueSlug(name), Cpf.digits(workshop.document())));

    var user = userRepository.save(UserEntity.newUser(
        tenant.getId(),
        owner.name().strip(),
        email,
        Cpf.digits(owner.cpf()),
        Phone.digits(owner.phone()),
        passwordEncoder.encode(owner.password()),
        Profile.ADMIN));

    events.publishEvent(
        new TenantRegistered(tenant.getId(), tenant.getName(), user.getUuid(), user.getName(),
            user.getEmail()));

    return authService.startSession(user);
  }

  private String uniqueSlug(String name) {
    var base = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    if (base.isEmpty()) base = "luthieria";
    if (base.length() > 50) base = base.substring(0, 50);

    var slug = base;
    while (tenantRepository.existsBySlug(slug)) {
      slug = base + "-" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36 * 36), 36);
    }
    return slug;
  }
}
