@RestController class Family4SafeRefactoringController {
    Family4SafeRefactoringReadService service;
    @GetMapping("/family-4/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family4SafeRefactoringReadService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
