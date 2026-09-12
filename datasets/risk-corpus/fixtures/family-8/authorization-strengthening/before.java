@RestController class Family8AuthorizationStrengtheningController {
    Family8AuthorizationStrengtheningService service;
    @GetMapping("/family-8/authorization-strengthening")
    Object read() { return service.read(); }
}
class Family8AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
