@RestController class Family5SafeProtectedAdditionController {
    Family5SafeProtectedAdditionService service;
    @PostMapping("/family-5/safe-protected-addition") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5SafeProtectedAdditionService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
