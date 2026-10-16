package com.luthier.notification.services.impl;

import com.luthier.notification.services.WhatsAppSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Envio pela WhatsApp Business Cloud API: POST /{phone-number-id}/messages com template. */
@Component
public class CloudApiWhatsAppSender implements WhatsAppSender {

  private final WhatsAppProperties props;
  private final RestClient client;

  public CloudApiWhatsAppSender(WhatsAppProperties props) {
    this.props = props;
    this.client = RestClient.builder().baseUrl(props.apiUrl()).build();
  }

  @Override
  public boolean isEnabled() {
    return props.configured();
  }

  @Override
  public void sendTemplate(String phoneDigits, String template, List<String> bodyParams,
      String buttonParam) {
    if (!isEnabled()) {
      throw new IllegalStateException("WhatsApp Cloud API não configurada");
    }
    List<Map<String, Object>> components = new ArrayList<>();
    components.add(Map.of("type", "body", "parameters",
        bodyParams.stream().map(p -> Map.of("type", "text", "text", p)).toList()));
    if (buttonParam != null) {
      components.add(Map.of("type", "button", "sub_type", "url", "index", "0",
          "parameters", List.of(Map.of("type", "text", "text", buttonParam))));
    }
    var body = Map.of(
        "messaging_product", "whatsapp",
        "to", "55" + phoneDigits,
        "type", "template",
        "template", Map.of(
            "name", template,
            "language", Map.of("code", props.language()),
            "components", components));

    client.post()
        .uri("/{phoneNumberId}/messages", props.phoneNumberId())
        .header("Authorization", "Bearer " + props.token())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }
}
