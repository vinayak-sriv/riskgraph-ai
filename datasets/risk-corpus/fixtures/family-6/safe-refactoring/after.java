@RestController class Family6SafeRefactoringController {
    Family6SafeRefactoringReadService service;
    @GetMapping("/family-6/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family6SafeRefactoringReadService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
