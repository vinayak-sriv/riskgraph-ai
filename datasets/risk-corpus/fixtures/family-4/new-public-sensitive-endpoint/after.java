@RestController class Family4NewPublicSensitiveEndpointController {
    Family4NewPublicSensitiveEndpointService service;
    @GetMapping("/family-4/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family4NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
