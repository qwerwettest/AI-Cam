package com.schedule.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {"com.schedule.server", "kvt"},
        exclude = {
                HibernateJpaAutoConfiguration.class,
                JpaRepositoriesAutoConfiguration.class
        }
)
public class ScheduleServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleServerApplication.class, args);
    }
}