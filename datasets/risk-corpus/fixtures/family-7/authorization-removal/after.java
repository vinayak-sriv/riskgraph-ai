@RestController class Family7AuthorizationRemovalController {
    Family7AuthorizationRemovalService service;
    @PostMapping("/family-7/authorization-removal")
    Object read() { return service.read(); }
}
class Family7AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
