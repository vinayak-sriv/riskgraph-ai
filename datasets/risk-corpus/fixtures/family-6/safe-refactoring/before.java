@RestController class Family6SafeRefactoringController {
    Family6SafeRefactoringService service;
    @GetMapping("/family-6/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family6SafeRefactoringService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
