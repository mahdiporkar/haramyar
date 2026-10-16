package ir.haramyar.rozeh.interfaces.rest;

import ir.haramyar.rozeh.application.query.ServiceCatalog;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous service description (Hive PUBLIC operation): which options exist and which provisional policies apply. */
@RestController
class PublicCatalogController {
  private final ServiceCatalog catalog;

  PublicCatalogController(ServiceCatalog catalog) { this.catalog = catalog; }

  @GetMapping("/public/catalog")
  ResponseEntity<ServiceCatalog> catalog() {
    return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(catalog);
  }
}
