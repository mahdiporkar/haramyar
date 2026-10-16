package ir.haramyar.rozeh;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Executable DDD/hexagonal boundaries of the Rozeh Booking module. */
@AnalyzeClasses(packages = "ir.haramyar.rozeh", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..rozeh.domain..")
      .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "javax.persistence..", "org.hibernate..",
          "com.fasterxml..", "org.keycloak..", "io.hiveplatform..", "com.rabbitmq..", "org.springframework.amqp..", "java.sql..", "ir.haramyar.kit..",
          "com.google.i18n..");

  @ArchTest
  static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
      .layer("Domain").definedBy("..rozeh.domain..")
      .layer("Application").definedBy("..rozeh.application..")
      .layer("Infrastructure").definedBy("..rozeh.infrastructure..")
      .layer("Interfaces").definedBy("..rozeh.interfaces..")
      .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
      .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces");

  @ArchTest
  static final ArchRule persistenceStaysInInfrastructure = noClasses().that().resideOutsideOfPackage("..rozeh.infrastructure..")
      .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..");
}
