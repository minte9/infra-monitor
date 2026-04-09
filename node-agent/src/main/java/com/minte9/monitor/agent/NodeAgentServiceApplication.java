/**
 * This is the Spring Boot entry point for node-agent.
 * 
 * Important:
 *  - @EnableScheduling turns on scheduled metric collection
 *  - @@EnableConfigurationProperties lets us bind application.yml
 *    into a typed Java properties class 
 */

package com.minte9.monitor.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.minte9.monitor.agent.config.NodeAgentProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NodeAgentProperties.class)
public class NodeAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NodeAgentServiceApplication.class, args);
    }
    
}
