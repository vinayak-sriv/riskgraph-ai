@RestController class Family2AuthorizationStrengtheningController {
    Family2AuthorizationStrengtheningService service;
    @GetMapping("/family-2/authorization-strengthening")
    Object read() { return service.read(); }
}
class Family2AuthorizationStrengtheningService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
