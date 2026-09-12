@RestController class Family0AmbiguousController {
    Family0AmbiguousService service;
    @GetMapping("/family-0/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family0AmbiguousService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
