package com.luthier.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfTest {

  @Test
  void acceptsValidCpfWithOrWithoutMask() {
    assertThat(Cpf.isValid("529.982.247-25")).isTrue();
    assertThat(Cpf.isValid("52998224725")).isTrue();
  }

  @Test
  void rejectsWrongCheckDigitsRepeatedDigitsAndWrongLength() {
    assertThat(Cpf.isValid("529.982.247-24")).isFalse();
    assertThat(Cpf.isValid("111.111.111-11")).isFalse();
    assertThat(Cpf.isValid("1234567890")).isFalse();
    assertThat(Cpf.isValid(null)).isFalse();
  }

  @Test
  void masksForLgpd() {
    assertThat(Cpf.mask("52998224725")).isEqualTo("***.982.247-**");
    assertThat(Cpf.mask(null)).isNull();
  }

  @Test
  void digitsStripsMaskAndBlankBecomesNull() {
    assertThat(Cpf.digits("529.982.247-25")).isEqualTo("52998224725");
    assertThat(Cpf.digits("  ")).isNull();
  }
}
