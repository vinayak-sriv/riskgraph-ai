@RestController class Family3AuthorizationRemovalController {
    Family3AuthorizationRemovalService service;
    @PostMapping("/family-3/authorization-removal") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family3AuthorizationRemovalService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
