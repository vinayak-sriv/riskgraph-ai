@RestController class Family0SafeProtectedAdditionController {
    Family0SafeProtectedAdditionService service;
    @GetMapping("/family-0/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family0SafeProtectedAdditionService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
