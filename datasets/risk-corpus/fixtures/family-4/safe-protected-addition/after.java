@RestController class Family4SafeProtectedAdditionController {
    Family4SafeProtectedAdditionService service;
    @GetMapping("/family-4/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family4SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
