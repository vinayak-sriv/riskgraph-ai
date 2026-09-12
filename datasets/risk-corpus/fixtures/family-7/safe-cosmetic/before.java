@RestController class Family7SafeCosmeticController {
    Family7SafeCosmeticService service;
    @PostMapping("/family-7/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family7SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
