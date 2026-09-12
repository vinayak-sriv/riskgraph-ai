@RestController class Family6AmbiguousController {
    Family6AmbiguousService service;
    @GetMapping("/family-6/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family6AmbiguousService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
