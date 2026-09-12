@RestController class Family9NewPublicSensitiveEndpointController {
    Family9NewPublicSensitiveEndpointService service;
    @PostMapping("/family-9/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family9NewPublicSensitiveEndpointService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
