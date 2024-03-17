package com.example.leader;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookRepository bookRepository;

    public Book getBookById(Long id) {
        return bookRepository.findById(id).orElseThrow();
    }
}
