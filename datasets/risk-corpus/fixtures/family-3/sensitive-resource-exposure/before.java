@RestController class Family3SensitiveResourceExposureController {
    Family3SensitiveResourceExposureService service;
    @PostMapping("/family-3/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family3SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
