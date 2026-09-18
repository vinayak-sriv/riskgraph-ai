package ai.riskgraph.analyzer.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringEndpointExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void expandsClassAndMethodPathsAndHonorsClassAuthorization() throws Exception {
        String relative = "src/ApiController.java";
        write(relative, """
                @RestController
                @RequestMapping({"/v1", "/v2"})
                @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
                class ApiController {
                    @RequestMapping(path={"/items", "/records"}, method={RequestMethod.GET, RequestMethod.POST})
                    Object list() { return null; }
                }
                """);

        var result = extractor().extract(tempDir, Map.of(relative, List.of(new ChangedRange(1, 100))));

        assertThat(result.endpoints()).hasSize(8);
        assertThat(result.endpoints()).extracting(value -> value.endpoint().endpoint())
                .contains("/v1/items", "/v1/records", "/v2/items", "/v2/records");
        assertThat(result.endpoints()).extracting(value -> value.endpoint().method())
                .containsOnly("GET", "POST");
        assertThat(result.endpoints()).allSatisfy(value -> {
            assertThat(value.endpoint().authentication()).isTrue();
            assertThat(value.endpoint().required_role()).isEqualTo("AUDITOR");
        });
    }

    @Test
    void reportsAuthorizationThatCannotBeReducedToAnMvpRole() throws Exception {
        String relative = "src/OwnerController.java";
        write(relative, """
                @RestController
                class OwnerController {
                    @GetMapping("/owned")
                    @PreAuthorize("authentication.name == #owner")
                    Object owned() { return null; }
                }
                """);

        var result = extractor().extract(tempDir, Map.of(relative, List.of(new ChangedRange(1, 100))));

        assertThat(result.endpoints()).singleElement().satisfies(value -> {
            assertThat(value.endpoint().authentication()).isTrue();
            assertThat(value.endpoint().required_role()).isNull();
        });
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo("WARNING");
            assertThat(diagnostic.code()).isEqualTo("UNRESOLVED_AUTHORIZATION");
        });
    }

    @Test
    void emitsOnlyTheEndpointWhoseMethodIntersectsTheChangedRange() throws Exception {
        String relative = "src/ScopedController.java";
        write(relative, """
                @RestController
                class ScopedController {
                    @GetMapping("/changed")
                    Object changed() { return null; }
                    @GetMapping("/unchanged")
                    Object unchanged() { return null; }
                }
                """);

        var result = extractor().extract(tempDir, Map.of(relative, List.of(new ChangedRange(3, 4))));

        assertThat(result.endpoints()).singleElement()
                .extracting(value -> value.endpoint().endpoint()).isEqualTo("/changed");
    }

    @Test
    void preservesMultipleServiceRepositoryPaths() throws Exception {
        String controller = "src/MultiController.java";
        write(controller, """
                @RestController
                class MultiController {
                    AlphaService alphaService; BetaService betaService;
                    @GetMapping("/combined")
                    Object combined() { alphaService.load(); return betaService.load(); }
                }
                """);
        write("src/AlphaService.java", """
                class AlphaService { AlphaRepository repository; Object load() { return repository.find(); } }
                """);
        write("src/BetaService.java", """
                class BetaService { BetaRepository repository; Object load() { return repository.find(); } }
                """);
        write("src/AlphaRepository.java", "interface AlphaRepository { Object find(); }");
        write("src/BetaRepository.java", "interface BetaRepository { Object find(); }");

        var result = extractor().extract(tempDir, Map.of(controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.dependency_paths()).hasSize(2);
            assertThat(endpoint.dependency_paths()).extracting(path -> path.repository())
                    .containsExactly("AlphaRepository", "BetaRepository");
        });
        assertThat(result.diagnostics()).extracting(value -> value.code()).contains("MULTIPLE_DEPENDENCY_PATHS");
    }

    @Test
    void recognizesSupportedAnnotationAuthorizationFamilies() throws Exception {
        String relative = "src/AuthController.java";
        write(relative, """
                @RestController
                class AuthController {
                    @GetMapping("/authority") @PreAuthorize("hasAuthority('EXPORT_READ')") Object authority() { return null; }
                    @GetMapping("/secured") @Secured("ROLE_ADMIN") Object secured() { return null; }
                    @GetMapping("/roles") @RolesAllowed({"AUDITOR", "ADMIN"}) Object roles() { return null; }
                }
                """);

        var result = extractor().extract(tempDir, Map.of(relative, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).extracting(value -> value.endpoint().required_role())
                .containsExactly("EXPORT_READ", "AUDITOR", "ADMIN");
        assertThat(result.endpoints()).allSatisfy(value -> assertThat(value.endpoint().authentication()).isTrue());
    }

    @Test
    void emitsEndpointWhenOnlyInvokedServiceMethodChanged() throws Exception {
        String controller = "src/ImpactController.java";
        String service = "src/ImpactService.java";
        write(controller, """
                @RestController class ImpactController {
                    ImpactService service;
                    @GetMapping("/impact") Object impact() { return service.load(); }
                }
                """);
        write(service, """
                class ImpactService {
                    ImpactRepository repository;
                    Object load() { return repository.find(); }
                }
                """);
        write("src/ImpactRepository.java", "interface ImpactRepository { Object find(); }");

        var result = extractor().extract(tempDir, Map.of(service, List.of(new ChangedRange(3, 3))));

        assertThat(result.endpoints()).singleElement()
                .extracting(value -> value.endpoint().endpoint()).isEqualTo("/impact");
    }

    @Test
    void usesServiceMethodAuthorizationForTheResolvedDependencyPath() throws Exception {
        String controller = "src/ServiceSecuredController.java";
        write(controller, """
                @RestController class ServiceSecuredController {
                    CustomerService service;
                    @GetMapping("/customers") Object customers() { return service.load(); }
                }
                """);
        write("src/CustomerService.java", """
                @Service class CustomerService {
                    CustomerRepository repository;
                    @PreAuthorize("hasRole('ADMIN')")
                    Object load() { return repository.findAll(); }
                }
                """);
        write("src/CustomerRepository.java", "interface CustomerRepository { Object findAll(); }");

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isTrue();
            assertThat(row.endpoint().required_role()).isEqualTo("ADMIN");
            assertThat(row.extraction_confidence().authorization()).isEqualTo("HIGH");
        });
    }

    @Test
    void mixedServiceAuthorizationForTheSameResourceFailsClosed() throws Exception {
        String controller = "src/MixedServiceAuthorizationController.java";
        write(controller, """
                @RestController class MixedServiceAuthorizationController {
                    CustomerService service;
                    @GetMapping("/customers") Object customers() {
                        service.securedLoad();
                        return service.openLoad();
                    }
                }
                """);
        write("src/CustomerService.java", """
                @Service class CustomerService {
                    CustomerRepository repository;
                    @PreAuthorize("hasRole('ADMIN')")
                    Object securedLoad() { return repository.findAll(); }
                    Object openLoad() { return repository.findAll(); }
                }
                """);
        write("src/CustomerRepository.java", "interface CustomerRepository { Object findAll(); }");

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 30))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isFalse();
            assertThat(row.endpoint().required_role()).isNull();
            assertThat(row.extraction_confidence().authorization()).isEqualTo("LOW");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .contains("AMBIGUOUS_SERVICE_AUTHORIZATION");
    }

    @Test
    void serviceSelfInvocationDoesNotProveProxyAuthorization() throws Exception {
        String controller = "src/SelfInvocationController.java";
        write(controller, """
                @RestController class SelfInvocationController {
                    CustomerService service;
                    @GetMapping("/customers") Object customers() { return service.load(); }
                }
                """);
        write("src/CustomerService.java", """
                @Service class CustomerService {
                    CustomerRepository repository;
                    Object load() { return securedLoad(); }
                    @PreAuthorize("hasRole('ADMIN')")
                    Object securedLoad() { return repository.findAll(); }
                }
                """);
        write("src/CustomerRepository.java", "interface CustomerRepository { Object findAll(); }");

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isFalse();
            assertThat(row.endpoint().required_role()).isNull();
        });
    }

    @Test
    void traversesResolvedControllerHelperToSensitiveRepository() throws Exception {
        String controller = "src/HelperController.java";
        write(controller, """
                @RestController class HelperController {
                    CustomerRepository repository;
                    @GetMapping("/customers") Object customers() { return loadCustomers(); }
                    Object loadCustomers() { return repository.findAll(); }
                }
                """);
        write("src/CustomerRepository.java", "interface CustomerRepository { Object findAll(); }");

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().repository()).isEqualTo("CustomerRepository");
            assertThat(row.endpoint().resource()).isEqualTo("Customer");
            assertThat(row.extraction_confidence().call_resolution()).isEqualTo("HIGH");
        });
    }

    @Test
    void unresolvedLocalHelperLowersCallAndOverallConfidence() throws Exception {
        String controller = "src/HelperOverloadController.java";
        write(controller, """
                @RestController class HelperOverloadController {
                    SafeRepository safe; CustomerRepository customers;
                    @GetMapping("/customers") Object endpoint() {
                        safe.find();
                        return helper(null);
                    }
                    Object helper(String id) { return customers.findAll(); }
                    Object helper(Integer id) { return customers.findAll(); }
                }
                """);
        write("src/SafeRepository.java", "interface SafeRepository { Object find(); }");
        write("src/CustomerRepository.java", "interface CustomerRepository { Object findAll(); }");

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 30))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().repository()).isEqualTo("SafeRepository");
            assertThat(row.extraction_confidence().call_resolution()).isEqualTo("LOW");
            assertThat(row.extraction_confidence().overall()).isEqualTo("LOW");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .contains("AMBIGUOUS_CALL_RESOLUTION");
    }

    @Test
    void reportsMalformedJavaWithoutReturningPartialEvidence() throws Exception {
        String relative = "src/Broken.java";
        write(relative, "class {");

        var result = extractor().extract(tempDir, Map.of(relative, List.of(new ChangedRange(1, 1))));

        assertThat(result.endpoints()).isEmpty();
        assertThat(result.diagnostics()).extracting(value -> value.code()).contains("SOURCE_NOT_PARSED");
    }

    @Test
    void scopesConventionalMultiModuleRepositoryToChangedSourceRoot() throws Exception {
        String changed = "complete/src/main/java/com/example/GreetingController.java";
        String completeApplication = "complete/src/main/java/com/example/RestServiceApplication.java";
        String initialApplication = "initial/src/main/java/com/example/RestServiceApplication.java";
        write(changed, """
                package com.example;
                @RestController class GreetingController {
                    @GetMapping("/greeting") Object greeting() { return null; }
                }
                """);
        write(completeApplication, "package com.example; class RestServiceApplication {}");
        write(initialApplication, "package com.example; class RestServiceApplication {}");

        var changedRange = List.of(new ChangedRange(1, 20));
        var result = extractor().extract(tempDir, Map.of(
                changed, changedRange,
                completeApplication, changedRange,
                initialApplication, changedRange));

        assertThat(result.endpoints()).singleElement()
                .extracting(value -> value.endpoint().endpoint()).isEqualTo("/greeting");
        assertThat(result.coverage().java_files_considered()).isEqualTo(3);
        assertThat(result.diagnostics()).extracting(value -> value.code())
                .doesNotContain("SPOON_MODEL_FAILED");
    }

    private void write(String relativePath, String contents) throws Exception {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    @Test
    void changedInjectedServiceTypeIncludesUnchangedEndpointBody() throws Exception {
        String controller="src/WiringController.java";
        write(controller,"""
            @RestController class WiringController {
                PaymentService service;
                @GetMapping("/export") Object export() { return service.load(); }
            }
            """);
        write("src/PaymentService.java","class PaymentService { PaymentRepository repository; Object load() { return repository.find(); } }");
        write("src/PaymentRepository.java","interface PaymentRepository { Object find(); }");
        var result=extractor().extract(tempDir,Map.of(controller,List.of(new ChangedRange(2,2))));
        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().resource()).isEqualTo("Payment");
            assertThat(row.endpoint().sensitivity()).isEqualTo("CRITICAL");
        });
    }

    @Test
    void complexRoleExpressionIsNotHighConfidence() throws Exception {
        String file = "src/ComplexController.java";
        write(file, """
            @RestController class ComplexController {
                @GetMapping("/export") @PreAuthorize("hasRole('ADMIN') or permitAll()")
                Object export() { return null; }
            }
            """);
        var result = extractor().extract(tempDir, Map.of(file, List.of(new ChangedRange(1, 20))));
        assertThat(result.endpoints()).singleElement().satisfies(row ->
            assertThat(row.extraction_confidence().authorization()).isEqualTo("LOW"));
        assertThat(result.diagnostics()).extracting(row -> row.code()).contains("COMPLEX_AUTHORIZATION");
    }

    @Test
    void resolvesConstantMappingAndKeepsExplicitEmptyMappingDistinct() throws Exception {
        String file = "src/ConstantController.java";
        write(file, """
            @RestController class ConstantController {
                static final String PATH = "/constant";
                @GetMapping(PATH) Object constant() { return null; }
                @GetMapping Object root() { return null; }
            }
            """);

        var result = extractor().extract(tempDir, Map.of(file, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).extracting(row -> row.endpoint().endpoint())
                .containsExactly("/", "/constant");
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .doesNotContain("UNRESOLVED_SPRING_MAPPING");
    }

    @Test
    void unresolvedMappingExpressionNeverBecomesRootRoute() throws Exception {
        String file = "src/UnknownController.java";
        write(file, """
            @RestController class UnknownController {
                @GetMapping(PATH) Object unknown() { return null; }
            }
            """);

        var result = extractor().extract(tempDir, Map.of(file, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(row -> {
            assertThat(row.code()).isEqualTo("UNRESOLVED_SPRING_MAPPING");
            assertThat(row.severity()).isEqualTo("WARNING");
        });
    }

    @Test
    void resolvesOverloadedMethodByArgumentTypeWithoutTraversingOtherOverload() throws Exception {
        String controller = "src/OverloadController.java";
        write(controller, """
            @RestController class OverloadController {
                LookupService service;
                @GetMapping("/lookup") Object lookup() { return service.load("id"); }
            }
            """);
        write("src/LookupService.java", """
            class LookupService {
                SafeRepository safe; CriticalRepository critical;
                Object load(String id) { return safe.find(); }
                Object load(Integer id) { return critical.find(); }
            }
            """);
        write("src/SafeRepository.java", "interface SafeRepository { Object find(); }");
        write("src/CriticalRepository.java", "interface CriticalRepository { Object find(); }");

        var result = extractor().extract(tempDir, Map.of(controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.dependency_paths()).extracting(path -> path.repository())
                    .containsExactly("SafeRepository");
            assertThat(row.extraction_confidence().call_resolution()).isEqualTo("HIGH");
        });
    }

    @Test
    void ambiguousOverloadDoesNotProduceConfirmedCandidatePath() throws Exception {
        String controller = "src/AmbiguousController.java";
        write(controller, """
            @RestController class AmbiguousController {
                LookupService service;
                @GetMapping("/ambiguous") Object lookup() { return service.load(null); }
            }
            """);
        write("src/LookupService.java", """
            class LookupService {
                AlphaRepository alpha; BetaRepository beta;
                Object load(String id) { return alpha.find(); }
                Object load(Integer id) { return beta.find(); }
            }
            """);
        write("src/AlphaRepository.java", "interface AlphaRepository { Object find(); }");
        write("src/BetaRepository.java", "interface BetaRepository { Object find(); }");

        var result = extractor().extract(tempDir, Map.of(controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.dependency_paths()).isEmpty();
            assertThat(row.extraction_confidence().call_resolution()).isEqualTo("LOW");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code()).contains("AMBIGUOUS_CALL_RESOLUTION");
    }

    @Test
    void qualifiedTypeReferenceDisambiguatesDuplicateSimpleClassNames() throws Exception {
        String controller = "src/app/QualifiedController.java";
        write(controller, """
            package app;
            @RestController class QualifiedController {
                alpha.LookupService service;
                @GetMapping("/qualified") Object lookup() { return service.load(); }
            }
            """);
        write("src/alpha/LookupService.java", """
            package alpha;
            public class LookupService { AlphaRepository repository; public Object load() { return repository.find(); } }
            """);
        write("src/alpha/AlphaRepository.java", "package alpha; interface AlphaRepository { Object find(); }");
        write("src/beta/LookupService.java", """
            package beta;
            public class LookupService { BetaRepository repository; public Object load() { return repository.find(); } }
            """);
        write("src/beta/BetaRepository.java", "package beta; interface BetaRepository { Object find(); }");

        var result = extractor().extract(tempDir, Map.of(controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row ->
                assertThat(row.dependency_paths()).extracting(path -> path.repository())
                        .containsExactly("AlphaRepository"));
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .doesNotContain("AMBIGUOUS_CALL_RESOLUTION");
    }

    @Test
    void securityFilterChainAuthenticatedRuleIsDegradedAsOutOfScope() throws Exception {
        String config = "src/SecurityConfig.java";
        write("src/PrivateController.java", """
            @RestController class PrivateController {
                @GetMapping("/private") Object privateData() { return null; }
            }
            """);
        write(config, """
            class SecurityConfig {
                SecurityFilterChain filter(Object http) {
                    http.authorizeHttpRequests(auth -> auth.requestMatchers("/private").authenticated());
                    return null;
                }
            }
            """);

        var result = extractor().extract(tempDir, Map.of(config, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isFalse();
            assertThat(row.endpoint().required_role()).isNull();
            assertThat(row.extraction_confidence().authorization()).isEqualTo("LOW");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .contains("UNRESOLVED_SECURITY_FILTER_CHAIN", "UNRESOLVED_ROUTE_AUTHORIZATION");
    }

    @Test
    void securityFilterChainPermitAllRuleStillDegradesAsOutOfScope() throws Exception {
        String config = "src/SecurityConfig.java";
        write("src/PublicController.java", """
            @RestController class PublicController {
                @GetMapping("/public") Object publicData() { return null; }
            }
            """);
        write(config, """
            class SecurityConfig {
                SecurityFilterChain filter(Object http) {
                    http.authorizeHttpRequests(auth -> auth.requestMatchers("/public").permitAll());
                    return null;
                }
            }
            """);

        var result = extractor().extract(tempDir, Map.of(config, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isFalse();
            assertThat(row.extraction_confidence().authorization()).isEqualTo("LOW");
        });
    }

    @Test
    void unsupportedSecurityFilterChainRuleDegradesWithoutInventingAuthorization() throws Exception {
        String config = "src/SecurityConfig.java";
        write("src/RoleController.java", """
            @RestController class RoleController {
                @GetMapping("/role-only") Object data() { return null; }
            }
            """);
        write(config, """
            class SecurityConfig {
                SecurityFilterChain filter(Object http) {
                    http.authorizeHttpRequests(auth -> auth.requestMatchers("/role-only").hasRole("ADMIN"));
                    return null;
                }
            }
            """);

        var result = extractor().extract(tempDir, Map.of(config, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().authentication()).isFalse();
            assertThat(row.extraction_confidence().authorization()).isEqualTo("LOW");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .contains("UNRESOLVED_SECURITY_FILTER_CHAIN", "UNRESOLVED_ROUTE_AUTHORIZATION");
    }

    @Test
    void unknownRepositoryResourceUsesConservativeMediumFallback() throws Exception {
        String controller = "src/ReportController.java";
        write(controller, """
            @RestController class ReportController {
                ReportService service;
                @GetMapping("/reports") Object reports() { return service.read(); }
            }
            class ReportService {
                AuditLedgerRepository repository;
                Object read() { return repository.findAll(); }
            }
            interface AuditLedgerRepository { Object findAll(); }
            """);

        var result = extractor().extract(tempDir, Map.of(
                controller, List.of(new ChangedRange(1, 20))));

        assertThat(result.endpoints()).singleElement().satisfies(row -> {
            assertThat(row.endpoint().resource()).isEqualTo("AuditLedger");
            assertThat(row.endpoint().sensitivity()).isEqualTo("MEDIUM");
            assertThat(row.dependency_paths()).singleElement().satisfies(path ->
                    assertThat(path.sensitivity()).isEqualTo("MEDIUM"));
            assertThat(row.sensitivity_evidence().matched_rule()).isEqualTo("default@1.1.0");
        });
        assertThat(result.diagnostics()).extracting(row -> row.code())
                .contains("SENSITIVITY_POLICY_DEFAULTED");
    }

    private SpringEndpointExtractor extractor() {
        return new SpringEndpointExtractor(new SensitivityPolicy(""));
    }
}
