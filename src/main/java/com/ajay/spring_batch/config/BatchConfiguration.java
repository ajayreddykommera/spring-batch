package com.ajay.spring_batch.config;

import com.ajay.spring_batch.listener.JobCompletionNotificationListener;
import com.ajay.spring_batch.listener.PersonItemReadListener;
import com.ajay.spring_batch.model.Person;
import com.ajay.spring_batch.repository.PersonRepository;
import com.ajay.spring_batch.service.BlobInputStreamResource;
import com.ajay.spring_batch.service.PersonItemProcessor;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class BatchConfiguration {
    private final PersonRepository personRepository;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final BlobServiceClient blobServiceClient;
    private final TableServiceClient tableServiceClient;

    @Value("${azure.storage.container_name}")
    private String containerName;

    @PostConstruct
    public void printTableServiceClient() {
        System.out.println(tableServiceClient);
    }

    @Bean
    public MultiResourceItemReader<Person> itemReader() {
        MultiResourceItemReader<Person> resourceItemReader = new MultiResourceItemReader<>();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        List<Resource> resources = new ArrayList<>();

        for (BlobItem blobItem : containerClient.listBlobs()) {
            BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
            resources.add(new BlobInputStreamResource(blobClient));
        }
        resourceItemReader.setResources(resources.toArray(new Resource[0]));
        resourceItemReader.setDelegate(reader());
        return resourceItemReader;
    }

    @Bean
    public FlatFileItemReader<Person> reader() {
        FlatFileItemReader<Person> flatFileItemReader = new FlatFileItemReader<>();
        flatFileItemReader.setName("csvReader");
        flatFileItemReader.setLinesToSkip(1);
        flatFileItemReader.setLineMapper(lineMapper());
        return flatFileItemReader;
    }

    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    //    @Bean
//    public RepositoryItemWriter<Person> writer() {
//        RepositoryItemWriter<Person> itemWriter = new RepositoryItemWriter<>();
//        itemWriter.setRepository(personRepository);
//        itemWriter.setMethodName("save");
//        return itemWriter;
//    }
    @Bean
    @DependsOn("tableServiceClient")
    public ItemWriter<Person> azureTableWriter() {
        return chunk -> {
            TableClient tableClient = tableServiceClient.createTableIfNotExists("test");
            System.out.println("tableClient" + tableClient);
            for (Person person : chunk.getItems()) {
                TableEntity tableEntity = new TableEntity("PersonPartition", person.getId())
                        .addProperty("first_name", person.getFirstName())
                        .addProperty("last_name", person.getLastName())
                        .addProperty("email", person.getEmail())
                        .addProperty("pone", person.getPhone())
                        .addProperty("dob", person.getDob());
                tableClient.createEntity(tableEntity);
            }
        };
    }

    @Bean
    public Step step1() {
        return new StepBuilder("csvStep", jobRepository)
                .<Person, Person>chunk(10, platformTransactionManager)
                .faultTolerant()
                .retryLimit(3)
                .retry(PessimisticLockingFailureException.class)
                .listener(new PersonItemReadListener())
                .reader(itemReader())
                .processor(processor())
//                .writer(writer())
                .writer(azureTableWriter())
                .build();
    }

    @Bean
    public Job job1() {
        return new JobBuilder("importStudents", jobRepository)
                .start(step1())
                .listener(new JobCompletionNotificationListener())
                .build();
    }

    private LineMapper<Person> lineMapper() {
        DefaultLineMapper<Person> lineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();
        lineTokenizer.setDelimiter(",");
        lineTokenizer.setStrict(false);
        lineTokenizer.setNames("id","firstname", "lastname", "email", "phone", "dob");
        BeanWrapperFieldSetMapper<Person> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(Person.class);
        lineMapper.setLineTokenizer(lineTokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);
        return lineMapper;
    }
}
