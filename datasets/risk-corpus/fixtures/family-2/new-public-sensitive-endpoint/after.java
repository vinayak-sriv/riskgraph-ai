@RestController class Family2NewPublicSensitiveEndpointController {
    Family2NewPublicSensitiveEndpointService service;
    @GetMapping("/family-2/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family2NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
