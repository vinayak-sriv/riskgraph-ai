@RestController class Family5AuthorizationRemovalController {
    Family5AuthorizationRemovalService service;
    @PostMapping("/family-5/authorization-removal") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
