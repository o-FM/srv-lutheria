package com.luthier.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** SH-01: sem tenant o padrão é o modo restrito; a visão de plataforma só existe se for pedida. */
class TenantContextTest {

  private static final UUID TENANT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID TENANT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

  private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void withoutTenantTheResolverUsesTheRestrictedSentinelNotRoot() {
    assertThat(TenantContext.isPlatform()).isFalse();

    var resolved = resolver.resolveCurrentTenantIdentifier();

    assertThat(resolved).isEqualTo(TenantIdentifierResolver.NO_TENANT);
    assertThat(resolver.isRoot(resolved)).isFalse();
  }

  @Test
  void tenantInContextIsResolved() {
    TenantContext.set(TENANT_A);

    assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TENANT_A);
  }

  @Test
  void platformScopeResolvesToRootOnlyWhileItRuns() {
    var inside = TenantContext.callAsPlatform(() -> {
      assertThat(TenantContext.isPlatform()).isTrue();
      assertThat(TenantContext.current()).isEmpty();
      return resolver.resolveCurrentTenantIdentifier();
    });

    assertThat(inside).isEqualTo(TenantIdentifierResolver.ROOT);
    assertThat(resolver.isRoot(inside)).isTrue();
    assertThat(TenantContext.isPlatform()).isFalse();
    assertThat(resolver.resolveCurrentTenantIdentifier())
        .isEqualTo(TenantIdentifierResolver.NO_TENANT);
  }

  @Test
  void tenantScopeInsidePlatformScopeWinsAndPlatformIsRestoredAfterwards() {
    TenantContext.callAsPlatform(() -> {
      var inner = TenantContext.callAs(TENANT_A, () -> {
        assertThat(TenantContext.isPlatform()).isFalse();
        return resolver.resolveCurrentTenantIdentifier();
      });
      assertThat(inner).isEqualTo(TENANT_A);
      assertThat(TenantContext.isPlatform()).isTrue();
      return null;
    });
  }

  @Test
  void platformScopeInsideTenantScopeHidesTheTenantAndRestoresIt() {
    TenantContext.runAs(TENANT_A, () -> {
      var inner = TenantContext.callAsPlatform(resolver::resolveCurrentTenantIdentifier);
      assertThat(inner).isEqualTo(TenantIdentifierResolver.ROOT);
      assertThat(TenantContext.current()).contains(TENANT_A);
      assertThat(TenantContext.isPlatform()).isFalse();
    });
  }

  @Test
  void previousStateIsRestoredWhenTheActionThrows() {
    TenantContext.set(TENANT_B);

    assertThatThrownBy(() -> TenantContext.callAsPlatform(() -> {
      throw new IllegalArgumentException("falhou");
    })).isInstanceOf(IllegalArgumentException.class);

    assertThat(TenantContext.current()).contains(TENANT_B);
    assertThat(TenantContext.isPlatform()).isFalse();
  }

  @Test
  void reservedIdsCannotBeUsedAsATenant() {
    for (var reserved : new UUID[] {TenantIdentifierResolver.ROOT,
        TenantIdentifierResolver.NO_TENANT}) {
      assertThat(TenantContext.isReserved(reserved)).isTrue();
      assertThatThrownBy(() -> TenantContext.set(reserved))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> TenantContext.runAs(reserved, () -> {}))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(TenantContext.isReserved(TENANT_A)).isFalse();
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void clearAlsoLeavesThePlatformScope() {
    TenantContext.callAsPlatform(() -> {
      TenantContext.clear();
      assertThat(TenantContext.isPlatform()).isFalse();
      return null;
    });
  }
}
