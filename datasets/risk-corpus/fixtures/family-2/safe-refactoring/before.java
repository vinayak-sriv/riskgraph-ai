@RestController class Family2SafeRefactoringController {
    Family2SafeRefactoringService service;
    @GetMapping("/family-2/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family2SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
