@RestController class Family2SafeProtectedAdditionController {
    Family2SafeProtectedAdditionService service;
    @GetMapping("/family-2/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family2SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
