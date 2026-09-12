@RestController class Family2AmbiguousController {
    Family2AmbiguousService service;
    @GetMapping("/family-2/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family2AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
