@RestController class Family9AuthorizationStrengtheningController {
    Family9AuthorizationStrengtheningService service;
    @PostMapping("/family-9/authorization-strengthening") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family9AuthorizationStrengtheningService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
