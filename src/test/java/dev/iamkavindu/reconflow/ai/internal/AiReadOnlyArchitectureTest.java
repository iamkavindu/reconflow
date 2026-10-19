package dev.iamkavindu.reconflow.ai.internal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AiReadOnlyArchitectureTest {

    @Test
    void aiExecutionCannotDependOnFinancialOrCaseWritePorts() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.iamkavindu.reconflow");
        noClasses().that().resideInAPackage("..ai..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Commands").check(classes);
        noClasses().that().resideInAPackage("..ai..")
                .should().dependOnClassesThat().haveSimpleName("InvestigationProcessing").check(classes);
    }
}
