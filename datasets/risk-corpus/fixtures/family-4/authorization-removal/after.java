@RestController class Family4AuthorizationRemovalController {
    Family4AuthorizationRemovalService service;
    @GetMapping("/family-4/authorization-removal")
    Object read() { return service.read(); }
}
class Family4AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
