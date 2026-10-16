package com.luthier.security.domain.records;

import com.luthier.security.domain.enums.Profile;
import jakarta.validation.constraints.NotNull;

public record UpdateProfileRequest(@NotNull Profile profile) {}
