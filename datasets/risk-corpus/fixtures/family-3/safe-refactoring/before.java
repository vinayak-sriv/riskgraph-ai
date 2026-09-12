@RestController class Family3SafeRefactoringController {
    Family3SafeRefactoringService service;
    @PostMapping("/family-3/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family3SafeRefactoringService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
