package com.example.worker2;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.partition.RemotePartitioningWorkerStepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.PlatformTransactionManager;


@Configuration
public class CustomSpringBatchConfiguration {

    Logger logger = LoggerFactory.getLogger(getClass());

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
    @StepScope
    ItemReader<Book> jdbcItemReader(DataSource dataSource,
                                    @Value("#{stepExecutionContext['partition']}") String partition
                                    ) {
        return new JdbcPagingItemReaderBuilder<Book>()
            .dataSource(dataSource)
            .sortKeys(Map.of("id", Order.ASCENDING))
            .selectClause("SELECT * ")
            .fromClause("FROM books")
            .whereClause("WHERE id IN (SELECT b.book_id from book_job_buckets b where b.bucket = :partition)")
            .parameterValues(Map.of("partition", partition))
            .rowMapper(new RowMapper<Book>() {

                @Override
                public Book mapRow(ResultSet arg0, int arg1) throws SQLException {
                    return Book.builder().id(arg0.getLong("id")).title(arg0.getString("title")).build();
                }
                
            })
            .pageSize(10)
            .name("jdbcItemReader")
            .build();
    }
    
    @Bean
    Step workerStep(JobRepository jobRepository, 
                    JobExplorer jobExplorer, 
                    ItemReader<Book> itemReader,
                    BeanFactory beanFactory,
                    PlatformTransactionManager platformTransactionManager,
                    @Qualifier("requests") MessageChannel requests,
                    @Qualifier("replies") MessageChannel replies
                    ) {
        return new RemotePartitioningWorkerStepBuilder("workerStep", jobRepository)
            .jobExplorer(jobExplorer)
            .beanFactory(beanFactory)
            .inputChannel(requests)
            .outputChannel(replies)
            .<Book, Book>chunk(3, platformTransactionManager)
            .reader(itemReader)
            .writer(new ItemWriter<Book>() {

                @Override
                public void write(Chunk<? extends Book> arg0) throws Exception {
                    arg0.forEach(System.out::println);
                }
                
            }).build();
    }

    @Bean
    IntegrationFlow repliesFlow(@Qualifier("replies") MessageChannel replies,
                                AmqpTemplate amqpTemplate
                                ) {
        return IntegrationFlow
            .from(replies)
            .handle(Amqp.outboundAdapter(amqpTemplate).routingKey("replies"))
            .get();
     }

    /*
     * copied from leader but switched inbound to request
     */
    @Bean
    IntegrationFlow requestFlow(ConnectionFactory connectionFactory,
                                @Qualifier("requests") MessageChannel requests
                                ) {
        SimpleMessageConverter simpleMessageConverter = new SimpleMessageConverter();
        simpleMessageConverter.setAllowedListPatterns(List.of("org.springframework.batch.integration.partition.StepExecutionRequest"));
        return IntegrationFlow
            .from(Amqp.inboundAdapter(connectionFactory, "requests").messageConverter(simpleMessageConverter))
            .channel(requests)
            .get();
    }
}
