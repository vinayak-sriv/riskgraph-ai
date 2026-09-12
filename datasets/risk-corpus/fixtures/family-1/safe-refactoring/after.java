@RestController class Family1SafeRefactoringController {
    Family1SafeRefactoringReadService service;
    @PostMapping("/family-1/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family1SafeRefactoringReadService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
