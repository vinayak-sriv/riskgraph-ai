@RestController class Family5AmbiguousController {
    Family5AmbiguousService service;
    @PostMapping("/family-5/ambiguous") @PreAuthorize("authentication.name == #owner")
    Object read() { return service.read(); }
}
class Family5AmbiguousService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
