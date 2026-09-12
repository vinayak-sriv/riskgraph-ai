@RestController class Family5SafeRefactoringController {
    Family5SafeRefactoringService service;
    @PostMapping("/family-5/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
