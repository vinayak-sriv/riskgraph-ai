@RestController class Family8SensitiveResourceExposureController {
    Family8SensitiveResourceExposureService service;
    @GetMapping("/family-8/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family8SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
