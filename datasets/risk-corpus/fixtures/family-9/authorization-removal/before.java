@RestController class Family9AuthorizationRemovalController {
    Family9AuthorizationRemovalService service;
    @PostMapping("/family-9/authorization-removal") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family9AuthorizationRemovalService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
