@RestController class Family3SafeRefactoringController {
    Family3SafeRefactoringReadService service;
    @PostMapping("/family-3/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family3SafeRefactoringReadService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
