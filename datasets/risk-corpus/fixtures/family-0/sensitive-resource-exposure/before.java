@RestController class Family0SensitiveResourceExposureController {
    Family0SensitiveResourceExposureService service;
    @GetMapping("/family-0/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family0SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
