package com.luthier.security.domain.records;

import jakarta.validation.constraints.Pattern;

/**
 * Preferências de aparência do usuário (cada pessoa escolhe a sua, em qualquer aparelho).
 * Valores iguais aos do front (ThemeContext). null = usar o padrão.
 */
public record UserPreferences(
    @Pattern(regexp = "system|light|dark", message = "Modo inválido") String themeMode,
    @Pattern(regexp = "ambar|azul|esmeralda|violeta|rosa|petroleo", message = "Cor inválida")
    String themeAccent,
    @Pattern(regexp = "quente|frio|ardosia", message = "Tom inválido") String themeTone) {}
