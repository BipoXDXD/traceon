package bipo.tech.traceon;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Regra da dependência dentro de cada módulo (ADR 0001): {@code domain} não conhece as outras camadas,
 * {@code application} só conhece {@code domain}, e {@code infrastructure} não conhece HTTP. A fronteira entre módulos
 * fica com o {@link ModularityTest}.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "bipo.tech.traceon";

    /** Módulos de negócio planejados (prompt mestre, seção 2) e os pacotes técnicos da Foundation. */
    private static final String[] MODULES = {
        "identity", "sites", "monitoring", "integrity", "findings", "notifications", "audit", "health", "shared"
    };

    @ArchTest
    static final ArchRule everyClassBelongsToAPlannedModule = classes()
            .should()
            .resideInAnyPackage(allowedPackages())
            .because("módulo novo é decisão de arquitetura: entra no prompt mestre ou num ADR antes de ganhar código");

    @ArchTest
    static final ArchRule domainDependsOnNoOtherLayer = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomain = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnDelivery = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "org.springframework.web..", "jakarta.servlet..")
            .allowEmptyShould(true);

    private static String[] allowedPackages() {
        Stream<String> modules = Arrays.stream(MODULES).map(module -> ROOT + "." + module + "..");
        return Stream.concat(Stream.of(ROOT), modules).toArray(String[]::new);
    }
}
