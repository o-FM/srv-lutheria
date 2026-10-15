/**
 * Shared kernel: erros, tenancy, validações e tipos comuns. Aberto para todos os módulos
 * (type = OPEN). Nunca coloque regra de negócio aqui.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package com.luthier.shared;

import org.springframework.modulith.ApplicationModule;
