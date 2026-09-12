@RestController class Family5SafeRefactoringController {
    Family5SafeRefactoringReadService service;
    @PostMapping("/family-5/safe-refactoring") @PreAuthorize("hasRole('ADMIN')")
    Object read() { return service.read(); }
}
class Family5SafeRefactoringReadService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
