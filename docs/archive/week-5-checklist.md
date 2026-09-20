# Week 5 Spring Endpoint and Authorization Extraction

Status: **Complete**

## Delivered

- [x] Build Spoon models in no-classpath mode without compiling or executing target code.
- [x] Extract `@RestController`/`@Controller` classes and method mappings.
- [x] Combine class and method paths, including multiple paths and HTTP methods.
- [x] Extract class-level and method-level `@PreAuthorize` roles.
- [x] Preserve authentication presence when an expression cannot be reduced to a role.
- [x] Emit a warning diagnostic for unsupported authorization expressions.
- [x] Attach repository-relative file and line locations to every endpoint observation.
- [x] Restrict output to controller files in the deterministic changed surface.

## Evidence

`SpringEndpointExtractorTest` covers route expansion, class authorization, explicit
HTTP methods, and unsupported authorization expressions. The real-commit integration
test proves that removing `@PreAuthorize` changes the emitted IR from authenticated
`ADMIN` access to unauthenticated access.
