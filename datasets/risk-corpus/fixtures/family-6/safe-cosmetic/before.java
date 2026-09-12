@RestController class Family6SafeCosmeticController {
    Family6SafeCosmeticService service;
    @GetMapping("/family-6/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family6SafeCosmeticService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
