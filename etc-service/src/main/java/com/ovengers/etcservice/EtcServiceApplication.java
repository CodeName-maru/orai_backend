package com.ovengers.etcservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.ovengers.etcservice", "com.ovengers.common"})
public class EtcServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtcServiceApplication.class, args);
    }

}
