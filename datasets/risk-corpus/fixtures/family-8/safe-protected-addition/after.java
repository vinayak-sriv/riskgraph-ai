@RestController class Family8SafeProtectedAdditionController {
    Family8SafeProtectedAdditionService service;
    @GetMapping("/family-8/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family8SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
