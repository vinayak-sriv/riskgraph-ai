@RestController class Family0AuthorizationStrengtheningController {
    Family0AuthorizationStrengtheningService service;
    @GetMapping("/family-0/authorization-strengthening") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family0AuthorizationStrengtheningService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
