@RestController class Family1SensitiveResourceExposureController {
    Family1SensitiveResourceExposureService service;
    @PostMapping("/family-1/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family1SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
