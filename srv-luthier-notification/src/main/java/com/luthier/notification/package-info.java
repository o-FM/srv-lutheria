/**
 * Notificações (e-mail, WhatsApp e alertas internos) disparadas por eventos dos outros módulos.
 * Nenhum módulo chama este diretamente: ele só ouve eventos públicos.
 */
@ApplicationModule(displayName = "Notificações",
    allowedDependencies = {"shared", "security", "customer", "product", "service"})
package com.luthier.notification;

import org.springframework.modulith.ApplicationModule;
