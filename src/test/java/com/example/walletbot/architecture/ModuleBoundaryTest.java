package com.example.walletbot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.example.walletbot",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String ROOT = "com.example.walletbot..";
    private static final String SHARED = "..walletbot.shared..";
    private static final String PLATFORM = "..walletbot.platform..";
    private static final String TELEGRAM = "..walletbot.telegram..";

    @ArchTest
    static final ArchRule telegram_is_not_depended_upon = noClasses()
            .that().resideOutsideOfPackage(TELEGRAM)
            .should().dependOnClassesThat().resideInAPackage(TELEGRAM)
            .because("telegram/ is a driving adapter — no module may depend on it");

    @ArchTest
    static final ArchRule shared_is_self_contained = noClasses()
            .that().resideInAPackage(SHARED)
            .should().dependOnClassesThat(
                    resideInAPackage(ROOT).and(not(resideInAPackage(SHARED))))
            .because("shared/ is the kernel — it must not depend on any feature, "
                    + "platform or interface module");

    @ArchTest
    static final ArchRule blockchain_depends_only_on_shared_and_platform = noClasses()
            .that().resideInAPackage("..walletbot.platform.blockchain..")
            .should().dependOnClassesThat(
                    resideInAPackage(ROOT)
                            .and(not(resideInAPackage(SHARED)))
                            .and(not(resideInAPackage(PLATFORM))))
            .because("platform/blockchain is pure infrastructure — only shared/ "
                    + "and platform/ are allowed");

    @ArchTest
    static final ArchRule application_and_domain_do_not_depend_on_api = noClasses()
            .that().resideInAnyPackage("..application..", "..domain..")
            .should().dependOnClassesThat().resideInAPackage("..api..")
            .because("application/ and domain/ are the inner layers — they must not "
                    + "depend on the api/ (HTTP) layer");
}
