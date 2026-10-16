package com.luthier.notification.services.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WhatsApp Business Cloud API (Meta). Sem phoneNumberId/token o envio fica desligado e as
 * mensagens são registradas como não enviadas.
 *
 * Templates esperados (criar e aprovar no WhatsApp Manager, idioma pt_BR):
 *  - osAberta (categoria UTILITY): "Olá {{1}}! Sua OS #{{2}} na {{3}} foi aberta.
 *    Acompanhe e aprove orçamentos em {{4}}"
 *  - codigoAcesso (categoria AUTHENTICATION, botão "copiar código"): "{{1}} é o seu código de acesso."
 */
@ConfigurationProperties(prefix = "luthieria.whatsapp")
public record WhatsAppProperties(
    @DefaultValue("https://graph.facebook.com/v21.0") String apiUrl,
    String phoneNumberId,
    String token,
    @DefaultValue("pt_BR") String language,
    @DefaultValue Templates templates,
    /** Só em desenvolvimento: escreve no log o código que seria enviado. */
    @DefaultValue("false") boolean logCodes) {

  public record Templates(
      @DefaultValue("luthieria_os_aberta") String osAberta,
      @DefaultValue("luthieria_codigo_acesso") String codigoAcesso) {}

  public boolean configured() {
    return phoneNumberId != null && !phoneNumberId.isBlank() && token != null && !token.isBlank();
  }
}
