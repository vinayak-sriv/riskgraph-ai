@RestController class Family3SafeCosmeticController {
    Family3SafeCosmeticService service;
    @PostMapping("/family-3/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family3SafeCosmeticService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
