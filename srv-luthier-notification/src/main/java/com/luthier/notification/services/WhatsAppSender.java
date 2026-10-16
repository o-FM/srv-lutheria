package com.luthier.notification.services;

import java.util.List;

/**
 * Envio automático de WhatsApp. Mensagens iniciadas pela empresa exigem templates aprovados pela
 * Meta; por isso a interface é por template + parâmetros.
 */
public interface WhatsAppSender {

  /** false quando a API não está configurada (nada é enviado). */
  boolean isEnabled();

  /**
   * @param phoneDigits DDD + número (o código do país 55 é adicionado aqui)
   * @param buttonParam parâmetro do botão (ex.: "copiar código" em templates de autenticação), ou null
   */
  void sendTemplate(String phoneDigits, String template, List<String> bodyParams, String buttonParam);
}
