@RestController class Family4AmbiguousController {
    Family4AmbiguousService service;
    @GetMapping("/family-4/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family4AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
