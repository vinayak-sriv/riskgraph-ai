@RestController class Family4SafeCosmeticController {
    Family4SafeCosmeticService service;
    @GetMapping("/family-4/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family4SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }

// Changed documentation formatting only.
