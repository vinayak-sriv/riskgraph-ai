@RestController class Family4SensitiveResourceExposureController {
    Family4SensitiveResourceExposureService service;
    @GetMapping("/family-4/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family4SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
