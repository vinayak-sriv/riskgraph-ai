@RestController class Family0AuthorizationRemovalController {
    Family0AuthorizationRemovalService service;
    @GetMapping("/family-0/authorization-removal")
    Object read() { return service.read(); }
}
class Family0AuthorizationRemovalService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
