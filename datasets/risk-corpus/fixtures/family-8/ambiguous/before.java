@RestController class Family8AmbiguousController {
    Family8AmbiguousService service;
    @GetMapping("/family-8/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family8AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
