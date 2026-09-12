@RestController class Family1AmbiguousController {
    Family1AmbiguousService service;
    @PostMapping("/family-1/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family1AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
