@RestController class Family9SafeCosmeticController {
    Family9SafeCosmeticService service;
    @PostMapping("/family-9/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family9SafeCosmeticService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
