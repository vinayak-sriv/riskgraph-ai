@RestController class Family6SafeProtectedAdditionController {
    Family6SafeProtectedAdditionService service;
    @GetMapping("/family-6/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family6SafeProtectedAdditionService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
