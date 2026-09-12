@RestController class Family3AmbiguousController {
    Family3AmbiguousService service;
    @PostMapping("/family-3/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family3AmbiguousService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
