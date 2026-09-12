@RestController class Family2SafeRefactoringController {
    Family2SafeRefactoringReadService service;
    @GetMapping("/family-2/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family2SafeRefactoringReadService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
