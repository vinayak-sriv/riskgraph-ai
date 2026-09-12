@RestController class Family8SafeCosmeticController {
    Family8SafeCosmeticService service;
    @GetMapping("/family-8/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family8SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }

// Changed documentation formatting only.
