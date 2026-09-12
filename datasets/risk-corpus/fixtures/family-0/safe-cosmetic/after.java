@RestController class Family0SafeCosmeticController {
    Family0SafeCosmeticService service;
    @GetMapping("/family-0/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family0SafeCosmeticService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }

// Changed documentation formatting only.
