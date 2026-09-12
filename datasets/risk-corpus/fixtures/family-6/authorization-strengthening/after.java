@RestController class Family6AuthorizationStrengtheningController {
    Family6AuthorizationStrengtheningService service;
    @GetMapping("/family-6/authorization-strengthening") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family6AuthorizationStrengtheningService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
