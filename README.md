# Home Infrastructure Monitor (VPS) - v1.3

- Project Structure, Metric Service (v1.2)
- MongoDB (v1.3)


## MongoDB

Replace in-memory repository with a Spring Data Mongo repository  
and mapping MatricRecord to a Mongo document.


### Key idea: Dependency Injection (DI)

    HTTP POST /api/metrics
            ↓
    MetricsController
            ↓
    MetricsIngestionService
            ↓
    MetricRepository (interface)
            ↓
    InMemoryMetricRepository (implementation)

### InMemoryMetricRepository

You NEVER create manually the repository (new InMemoryMetricRepository()),    
Spring does it for you.  

Spring sees your repository implementation exists.

    @Repository
    public class InMemoryMetricRepository implements MetricRepository {

Because of @Repository, Spring:

- detects it during startup (component scanning)
- creates an instance (a “bean”)

Spring sees your service needs a repository.  
Spring resolves the dependency.   
Spring looks for MetricRepository and finds InMemoryMetricRepository.  

So Spring does internally:

    MetricRepository repo = new InMemoryMetricRepository();
    MetricsIngestionService service = new MetricsIngestionService(repo);

Why you don't see it being called:

- you depend on the interface
- Spring inject the implementation
- Java polymorphism resolves at runtime

If you had multiple implementations, Spring would complain.

    InMemoryMetricRepository
    MongoMetricRepository
    -- NoUniqueBeanDefinitionException

Now, we will replace InMemoryMetricRepository with MongoMetricRepository.  
And we will NOT change controller and service, only the implementation.   