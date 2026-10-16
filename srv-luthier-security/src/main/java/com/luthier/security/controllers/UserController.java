package com.luthier.security.controllers;

import com.luthier.security.domain.records.CreateUserRequest;
import com.luthier.security.domain.records.UpdateProfileRequest;
import com.luthier.security.domain.records.UserPreferences;
import com.luthier.security.domain.records.UserResponse;
import com.luthier.security.services.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

  private final UserManagementService userManagementService;

  public UserController(UserManagementService userManagementService) {
    this.userManagementService = userManagementService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return userManagementService.create(request);
  }

  @GetMapping
  public List<UserResponse> list() {
    return userManagementService.list();
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return userManagementService.me(UUID.fromString(jwt.getClaimAsString("uid")));
  }

  /** Preferências de aparência do usuário logado (modo, cor de destaque, tom). */
  @PutMapping("/me/preferences")
  public UserResponse updateMyPreferences(@AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UserPreferences preferences) {
    return userManagementService.updateMyPreferences(
        UUID.fromString(jwt.getClaimAsString("uid")), preferences);
  }

  @PatchMapping("/{id}/profile")
  public UserResponse changeProfile(@PathVariable UUID id,
      @Valid @RequestBody UpdateProfileRequest request) {
    return userManagementService.changeProfile(id, request.profile());
  }

  @PostMapping("/{id}/activate")
  public UserResponse activate(@PathVariable UUID id) {
    return userManagementService.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  public UserResponse deactivate(@PathVariable UUID id) {
    return userManagementService.deactivate(id);
  }
}
