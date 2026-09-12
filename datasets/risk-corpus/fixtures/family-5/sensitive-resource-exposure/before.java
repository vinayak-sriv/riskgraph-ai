@RestController class Family5SensitiveResourceExposureController {
    Family5SensitiveResourceExposureService service;
    @PostMapping("/family-5/sensitive-resource-exposure")
    Object read() { return service.read(); }
}
class Family5SensitiveResourceExposureService { CatalogRepository repository; Object read() { return repository.findAll(); } }
interface CatalogRepository { Object findAll(); }
