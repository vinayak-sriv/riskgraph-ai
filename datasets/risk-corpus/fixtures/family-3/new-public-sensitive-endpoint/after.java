@RestController class Family3NewPublicSensitiveEndpointController {
    Family3NewPublicSensitiveEndpointService service;
    @PostMapping("/family-3/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family3NewPublicSensitiveEndpointService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
