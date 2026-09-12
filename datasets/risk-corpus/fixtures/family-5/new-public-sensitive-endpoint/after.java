@RestController class Family5NewPublicSensitiveEndpointController {
    Family5NewPublicSensitiveEndpointService service;
    @PostMapping("/family-5/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family5NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
