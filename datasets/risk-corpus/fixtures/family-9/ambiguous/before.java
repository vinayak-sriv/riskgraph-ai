@RestController class Family9AmbiguousController {
    Family9AmbiguousService service;
    @PostMapping("/family-9/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family9AmbiguousService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
