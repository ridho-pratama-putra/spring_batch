DROP TABLE book_job_buckets;
CREATE TABLE book_job_buckets (
  book_id bigint references books(id),
  bucket text not null
);