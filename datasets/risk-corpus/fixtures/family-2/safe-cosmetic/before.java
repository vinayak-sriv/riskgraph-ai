@RestController class Family2SafeCosmeticController {
    Family2SafeCosmeticService service;
    @GetMapping("/family-2/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family2SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
