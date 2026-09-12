@RestController class Family3SafeProtectedAdditionController {
    Family3SafeProtectedAdditionService service;
    @PostMapping("/family-3/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family3SafeProtectedAdditionService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
