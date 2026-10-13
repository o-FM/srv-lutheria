package com.luthier.domain.records;

import com.luthier.domain.enums.Profile;
import jakarta.validation.constraints.NotNull;

public record UpdateProfileRequest(@NotNull Profile profile) {}
