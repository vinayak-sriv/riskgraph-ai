@RestController class Family8SensitiveResourceExposureController {
    Family8SensitiveResourceExposureService service;
    @GetMapping("/family-8/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family8SensitiveResourceExposureService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
