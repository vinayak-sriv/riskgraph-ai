@RestController class Family9SafeProtectedAdditionController {
    Family9SafeProtectedAdditionService service;
    @PostMapping("/family-9/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family9SafeProtectedAdditionService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
