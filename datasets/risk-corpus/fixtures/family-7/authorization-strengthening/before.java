@RestController class Family7AuthorizationStrengtheningController {
    Family7AuthorizationStrengtheningService service;
    @PostMapping("/family-7/authorization-strengthening")
    Object read() { return service.read(); }
}
class Family7AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
