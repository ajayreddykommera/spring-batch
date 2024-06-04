package com.ajay.spring_batch.service;

import com.ajay.spring_batch.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
public class PersonItemProcessor implements ItemProcessor<Person, Person> {

    @Override
    public Person process(Person item) throws Exception {
        log.info("person processor {}", item);
        return item;
    }
}
