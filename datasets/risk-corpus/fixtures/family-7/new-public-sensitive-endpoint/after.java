@RestController class Family7NewPublicSensitiveEndpointController {
    Family7NewPublicSensitiveEndpointService service;
    @PostMapping("/family-7/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family7NewPublicSensitiveEndpointService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
