@RestController class Family1SafeCosmeticController {
    Family1SafeCosmeticService service;
    @PostMapping("/family-1/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family1SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }

// Changed documentation formatting only.
