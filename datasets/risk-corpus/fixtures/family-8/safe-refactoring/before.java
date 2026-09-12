@RestController class Family8SafeRefactoringController {
    Family8SafeRefactoringService service;
    @GetMapping("/family-8/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family8SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
