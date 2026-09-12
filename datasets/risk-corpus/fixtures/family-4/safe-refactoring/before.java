@RestController class Family4SafeRefactoringController {
    Family4SafeRefactoringService service;
    @GetMapping("/family-4/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family4SafeRefactoringService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
