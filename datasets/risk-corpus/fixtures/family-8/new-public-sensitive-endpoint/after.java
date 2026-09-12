@RestController class Family8NewPublicSensitiveEndpointController {
    Family8NewPublicSensitiveEndpointService service;
    @GetMapping("/family-8/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family8NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
