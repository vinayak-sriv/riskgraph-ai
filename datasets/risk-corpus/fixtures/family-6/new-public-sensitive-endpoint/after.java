@RestController class Family6NewPublicSensitiveEndpointController {
    Family6NewPublicSensitiveEndpointService service;
    @GetMapping("/family-6/new-public-sensitive-endpoint")
    Object read() { return service.read(); }
}
class Family6NewPublicSensitiveEndpointService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
