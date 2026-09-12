@RestController class Family4AuthorizationStrengtheningController {
    Family4AuthorizationStrengtheningService service;
    @GetMapping("/family-4/authorization-strengthening")
    Object read() { return service.read(); }
}
class Family4AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
