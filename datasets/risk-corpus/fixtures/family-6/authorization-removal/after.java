@RestController class Family6AuthorizationRemovalController {
    Family6AuthorizationRemovalService service;
    @GetMapping("/family-6/authorization-removal")
    Object read() { return service.read(); }
}
class Family6AuthorizationRemovalService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
