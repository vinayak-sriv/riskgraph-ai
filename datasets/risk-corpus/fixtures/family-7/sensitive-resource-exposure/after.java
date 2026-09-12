@RestController class Family7SensitiveResourceExposureController {
    Family7SensitiveResourceExposureService service;
    @PostMapping("/family-7/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family7SensitiveResourceExposureService { CustomerRepository repository; Object read() { return repository.findAll(); } }
interface CustomerRepository { Object findAll(); }
