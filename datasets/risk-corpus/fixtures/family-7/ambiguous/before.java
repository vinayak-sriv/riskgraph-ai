@RestController class Family7AmbiguousController {
    Family7AmbiguousService service;
    @PostMapping("/family-7/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family7AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
