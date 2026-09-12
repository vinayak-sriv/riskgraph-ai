@RestController class Family9SensitiveResourceExposureController {
    Family9SensitiveResourceExposureService service;
    @PostMapping("/family-9/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family9SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
