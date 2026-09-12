@RestController class Family9SafeRefactoringController {
    Family9SafeRefactoringReadService service;
    @PostMapping("/family-9/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family9SafeRefactoringReadService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
