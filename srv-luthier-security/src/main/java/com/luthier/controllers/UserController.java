package com.luthier.controllers;

import com.luthier.domain.records.CreateUserRequest;
import com.luthier.domain.records.UserResponse;
import com.luthier.services.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
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
}
