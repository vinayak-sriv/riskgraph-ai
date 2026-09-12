@RestController class Family7SafeRefactoringController {
    Family7SafeRefactoringService service;
    @PostMapping("/family-7/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family7SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
