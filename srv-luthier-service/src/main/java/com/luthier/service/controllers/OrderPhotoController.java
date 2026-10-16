package com.luthier.service.controllers;

import com.luthier.service.domain.records.AddPhotoRequest;
import com.luthier.service.domain.records.PhotoContent;
import com.luthier.service.domain.records.PhotoResponse;
import com.luthier.service.services.OrderPhotoService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/service-orders/{id}/photos")
public class OrderPhotoController {

  private final OrderPhotoService photos;

  public OrderPhotoController(OrderPhotoService photos) {
    this.photos = photos;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PhotoResponse add(@PathVariable UUID id, @Valid @RequestBody AddPhotoRequest request,
      Principal principal) {
    return photos.add(id, request, principal.getName());
  }

  @GetMapping
  public List<PhotoResponse> list(@PathVariable UUID id) {
    return photos.list(id);
  }

  @GetMapping("/{photoId}/content")
  public ResponseEntity<byte[]> content(@PathVariable UUID id, @PathVariable UUID photoId) {
    return image(photos.content(id, photoId));
  }

  @DeleteMapping("/{photoId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, @PathVariable UUID photoId) {
    photos.delete(id, photoId);
  }

  /** Foto é imutável: pode ficar em cache do navegador, mas nunca em cache compartilhado. */
  static ResponseEntity<byte[]> image(PhotoContent content) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
        .body(content.data());
  }
}
