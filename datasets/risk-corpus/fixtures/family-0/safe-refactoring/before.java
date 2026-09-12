@RestController class Family0SafeRefactoringController {
    Family0SafeRefactoringService service;
    @GetMapping("/family-0/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family0SafeRefactoringService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
