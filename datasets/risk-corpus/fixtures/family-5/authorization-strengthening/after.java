@RestController class Family5AuthorizationStrengtheningController {
    Family5AuthorizationStrengtheningService service;
    @PostMapping("/family-5/authorization-strengthening") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
