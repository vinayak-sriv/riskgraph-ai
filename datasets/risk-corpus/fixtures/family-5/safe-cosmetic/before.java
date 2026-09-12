@RestController class Family5SafeCosmeticController {
    Family5SafeCosmeticService service;
    @PostMapping("/family-5/safe-cosmetic") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5SafeCosmeticService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
