package ir.haramyar.martyrs.interfaces.rest;

import ir.haramyar.kit.security.CurrentActor;
import ir.haramyar.kit.security.HaramyarSecurityProperties;
import ir.haramyar.martyrs.application.ProfileTranslations;
import ir.haramyar.martyrs.domain.EditorialPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProfileTranslationsController {
  private final ProfileTranslations translations;
  private final HaramyarSecurityProperties security;
  private final EditorialPolicy policy;

  ProfileTranslationsController(ProfileTranslations translations, HaramyarSecurityProperties security, EditorialPolicy policy) {
    this.translations = translations;
    this.security = security;
    this.policy = policy;
  }

  record TranslationBody(@NotBlank String fullName, String honorific, @NotBlank String shortDescription,
      @NotBlank String biography, String seoTitle, String seoDescription, Long expectedVersion) {}
  record VersionBody(@NotNull Long expectedVersion) {}

  @GetMapping("/editorial/profiles/{profileId}/translations")
  List<ProfileTranslations.Translation> editorial(@PathVariable UUID profileId) {
    staff();
    return translations.editorial(profileId);
  }

  @PutMapping("/editorial/profiles/{profileId}/translations/{locale}")
  List<ProfileTranslations.Translation> save(@PathVariable UUID profileId, @PathVariable String locale,
      @Valid @RequestBody TranslationBody body) {
    staff();
    return translations.save(profileId, locale, body.fullName(), body.honorific(), body.shortDescription(),
        body.biography(), body.seoTitle(), body.seoDescription(), body.expectedVersion());
  }

  @PostMapping("/editorial/profiles/{profileId}/translations/{locale}/submit")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void submit(@PathVariable UUID profileId, @PathVariable String locale, @Valid @RequestBody VersionBody body) {
    translations.transition(profileId, locale, body.expectedVersion(), staff(), false, policy.fourEyes());
  }

  @PostMapping("/editorial/profiles/{profileId}/translations/{locale}/publish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void publish(@PathVariable UUID profileId, @PathVariable String locale, @Valid @RequestBody VersionBody body) {
    translations.transition(profileId, locale, body.expectedVersion(), staff(), true, policy.fourEyes());
  }

  private UUID staff() { return CurrentActor.requireStaff(security.staffRole()).userId(); }
}
