@RestController class Family1AuthorizationStrengtheningController {
    Family1AuthorizationStrengtheningService service;
    @PostMapping("/family-1/authorization-strengthening") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family1AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
