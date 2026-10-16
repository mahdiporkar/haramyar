package ir.haramyar.martyrs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "ir.haramyar.martyrs", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  @ArchTest
  static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage("..martyrs.domain..")
      .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml..", "software.amazon..",
          "io.hiveplatform..", "ir.haramyar.kit..", "java.sql..");

  @ArchTest
  static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
      .layer("Domain").definedBy("..martyrs.domain..")
      .layer("Application").definedBy("..martyrs.application..")
      .layer("Infrastructure").definedBy("..martyrs.infrastructure..")
      .layer("Interfaces").definedBy("..martyrs.interfaces..")
      .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
      .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure");

  @ArchTest
  static final ArchRule noModuleCoupling = noClasses().that().resideInAPackage("ir.haramyar.martyrs..")
      .should().dependOnClassesThat().resideInAPackage("ir.haramyar.rozeh..");
}
