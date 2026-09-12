@RestController class Family3AuthorizationStrengtheningController {
    Family3AuthorizationStrengtheningService service;
    @PostMapping("/family-3/authorization-strengthening")
    Object read() { return service.read(); }
}
class Family3AuthorizationStrengtheningService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
