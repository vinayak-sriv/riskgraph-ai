@RestController class Family1NewPublicSensitiveEndpointController {
    Family1NewPublicSensitiveEndpointService service;
    @PostMapping("/family-1/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family1NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
