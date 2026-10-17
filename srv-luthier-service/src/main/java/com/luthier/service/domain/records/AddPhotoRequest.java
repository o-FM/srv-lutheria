package com.luthier.service.domain.records;

import com.luthier.service.domain.enums.PhotoTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Foto vinda da câmera do celular como data URL ({@code data:image/jpeg;base64,...}).
 * O front reduz a imagem antes de enviar; o limite aqui (~3 MB de imagem) é a última barreira.
 */
public record AddPhotoRequest(
    @NotNull PhotoTag tag,
    @Size(max = 200) String caption,
    @NotBlank @Size(max = 4_200_000) String dataUrl) {

  @Override
  public String toString() {
    return "AddPhotoRequest[tag=" + tag + ", caption=" + caption + ", dataUrl=<"
        + (dataUrl == null ? 0 : dataUrl.length()) + " chars>]";
  }
}
