@RestController class Family2AuthorizationRemovalController {
    Family2AuthorizationRemovalService service;
    @GetMapping("/family-2/authorization-removal") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family2AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
