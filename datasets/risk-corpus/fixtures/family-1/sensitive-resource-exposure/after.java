@RestController class Family1SensitiveResourceExposureController {
    Family1SensitiveResourceExposureService service;
    @PostMapping("/family-1/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family1SensitiveResourceExposureService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
