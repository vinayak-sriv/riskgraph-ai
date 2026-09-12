@RestController class Family7SafeProtectedAdditionController {
    Family7SafeProtectedAdditionService service;
    @PostMapping("/family-7/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family7SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
