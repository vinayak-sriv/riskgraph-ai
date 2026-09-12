@RestController class Family1SafeProtectedAdditionController {
    Family1SafeProtectedAdditionService service;
    @PostMapping("/family-1/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family1SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
