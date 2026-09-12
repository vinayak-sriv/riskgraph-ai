@RestController class Family0NewPublicSensitiveEndpointController {
    Family0NewPublicSensitiveEndpointService service;
    @GetMapping("/family-0/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family0NewPublicSensitiveEndpointService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
