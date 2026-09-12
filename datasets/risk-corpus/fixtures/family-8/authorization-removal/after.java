@RestController class Family8AuthorizationRemovalController {
    Family8AuthorizationRemovalService service;
    @GetMapping("/family-8/authorization-removal")
    Object read() { return service.read(); }
}
class Family8AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
