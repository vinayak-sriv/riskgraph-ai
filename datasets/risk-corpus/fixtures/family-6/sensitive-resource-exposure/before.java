@RestController class Family6SensitiveResourceExposureController {
    Family6SensitiveResourceExposureService service;
    @GetMapping("/family-6/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family6SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
