package com.example.leader;

import java.util.List;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.partition.RemotePartitioningManagerStepBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

@Configuration
public class CustomSpringBatchConfiguration {

    @Bean
    DirectChannelSpec replies() {
        return MessageChannels.direct();
    }

    @Bean
    DirectChannelSpec requests() {
        return MessageChannels.direct();
    }

    @Bean
    Queue repliesQueue() {
        return new Queue("replies");
    }

    @Bean
    Queue requestsQueue() {
        return new Queue("requests");
    }


    @Bean
    IntegrationFlow requestsFlow(MessageChannel requests,
                                AmqpTemplate amqpTemplate
                                ) {
        return IntegrationFlow
            .from(requests)
            .handle(Amqp.outboundAdapter(amqpTemplate).routingKey("requests"))
            .get();
     }

    @Bean
    IntegrationFlow repliesFlow(ConnectionFactory connectionFactory,
                                MessageChannel replies
                                ) {
        SimpleMessageConverter simpleMessageConverter = new SimpleMessageConverter();
        simpleMessageConverter.setAllowedListPatterns(List.of("org.springframework.batch.core.StepExecution", 
                                                                "org.springframework.batch.core.Entity", 
                                                                "java.time.Ser",
                                                                "org.springframework.batch.item.ExecutionContext",
                                                                "java.util.concurrent.ConcurrentHashMap",
                                                                "java.util.concurrent.ConcurrentHashMap$Segment",
                                                                "java.util.concurrent.locks.ReentrantLock",
                                                                "java.util.concurrent.locks.ReentrantLock$NonfairSync",
                                                                "java.util.concurrent.locks.ReentrantLock$Sync",
                                                                "java.util.concurrent.locks.AbstractQueuedSynchronizer",
                                                                "java.util.concurrent.locks.AbstractOwnableSynchronizer",
                                                                "org.springframework.batch.core.ExitStatus",
                                                                "org.springframework.batch.core.JobExecution",
                                                                "org.springframework.batch.core.JobInstance",
                                                                "org.springframework.batch.core.JobParameters",
                                                                "java.util.HashMap",
                                                                "org.springframework.batch.core.JobParameter",
                                                                "org.springframework.batch.core.BatchStatus",
                                                                "java.lang.Enum",
                                                                "java.util.Collections$SynchronizedSet",
                                                                "java.util.Collections$SynchronizedCollection",
                                                                "*"
                                                                ));
        return IntegrationFlow
            .from(Amqp.inboundAdapter(connectionFactory, "replies").messageConverter(simpleMessageConverter))
            .channel(replies)
            .get();
    }
    
    @Bean
    Step managerStep(JobRepository jobRepository, 
                    BeanFactory beanFactory,
                    SpringTipsPartitioner springTipsPartitioner,
                    JobExplorer jobExplorer,
                    @Qualifier("requests") MessageChannel requests,
                    @Qualifier("replies") MessageChannel replies
                    ) {
        int gridSize = 10;
        return new RemotePartitioningManagerStepBuilder("managerStep", jobRepository)
            .partitioner("workerStep", springTipsPartitioner)
            .beanFactory(beanFactory)
            .gridSize(gridSize)
            .jobExplorer(jobExplorer)
            .inputChannel(replies)
            .outputChannel(requests)
            .build();
    }

    @Bean
    Job job(JobRepository jobRepository, Step managerStep) {
        return new JobBuilder("job", jobRepository)
            .start(managerStep)
            .incrementer(new RunIdIncrementer())
            .build();
    }
}
