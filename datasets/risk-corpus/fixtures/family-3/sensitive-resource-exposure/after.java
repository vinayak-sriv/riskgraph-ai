@RestController class Family3SensitiveResourceExposureController {
    Family3SensitiveResourceExposureService service;
    @PostMapping("/family-3/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family3SensitiveResourceExposureService { PaymentRepository repository; Object read() { return repository.findAll(); } }
interface PaymentRepository { Object findAll(); }
