package com.luthier.services.impl;

import com.luthier.domain.repositories.TenantRepository;
import com.luthier.domain.repositories.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;

  public UserServiceImpl(UserRepository userRepository, TenantRepository tenantRepository) {
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    var userEntity = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    return User.withUsername(userEntity.getEmail())
        .password(userEntity.getPassword())
        .roles(userEntity.getProfile().getValue())
        // Luthieria desativada bloqueia todos os seus usuários (mesma resposta genérica no login)
        .disabled(!userEntity.isActive() || !tenantRepository.isActive(userEntity.getTenantId()))
        .build();
  }
}
