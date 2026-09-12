@RestController class Family1SafeRefactoringController {
    Family1SafeRefactoringService service;
    @PostMapping("/family-1/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family1SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
