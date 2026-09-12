@RestController class Family1AuthorizationRemovalController {
    Family1AuthorizationRemovalService service;
    @PostMapping("/family-1/authorization-removal")
    Object read() { return service.read(); }
}
class Family1AuthorizationRemovalService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
