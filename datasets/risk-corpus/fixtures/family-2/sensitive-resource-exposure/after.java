@RestController class Family2SensitiveResourceExposureController {
    Family2SensitiveResourceExposureService service;
    @GetMapping("/family-2/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family2SensitiveResourceExposureService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
