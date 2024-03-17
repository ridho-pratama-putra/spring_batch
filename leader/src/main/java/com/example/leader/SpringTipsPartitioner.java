package com.example.leader;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/*
    * take gridSize
    * tell worker to process assigned range of data via SQL
    * job to send email example
    */
@Component
@RequiredArgsConstructor
public class SpringTipsPartitioner implements Partitioner {

    private final JdbcClient jdbcClient;
    private final String sql_POSTGRE = """
        insert into book_job_buckets(id, bucket)
            select id, 'partition' || ntile(?)
            over (order by id) as bucket
            from books
    """;
    private final String sql_MYSQL_ROW = """
        SET @row_number = 0;
    """;
    private final String sql_MYSQL_COL = """
        SET @bucket_size = CEIL((SELECT COUNT(*) FROM books) / ?);
    """;
    private final String sql_MYSQL_BUCKET = """
        SET @bucket = 1;
    """;
    private final String sql_MYSQL = """    
        INSERT INTO book_job_buckets(book_id, bucket)    
        SELECT id, 
                CASE
                    WHEN (@row_number := @row_number + 1) <= @bucket_size * @bucket THEN CONCAT('partition', @bucket)
                    ELSE CONCAT('partition', @bucket := @bucket + 1)
                END AS bucket
        FROM books;
    """;


    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        jdbcClient.sql(sql_MYSQL_COL).param(gridSize).update();
        jdbcClient.sql(sql_MYSQL_ROW).update();
        jdbcClient.sql(sql_MYSQL_BUCKET).update();
        jdbcClient.sql(sql_MYSQL).update();

        HashMap<String, ExecutionContext> hashMap = new HashMap<String, ExecutionContext>();
        for (int i = 0; i < gridSize; i++) {
            String partitionName = "partition" + (i+1);
            hashMap.put(partitionName, new ExecutionContext(Map.of("partition", partitionName)));
        }
        return hashMap;
    }
}
