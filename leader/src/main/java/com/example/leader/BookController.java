package com.example.leader;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class BookController {
    private final BookService bookService;

    @GetMapping("/book")
    public ResponseEntity<Book> getBook(@RequestParam Long id) {
        Book result = bookService.getBookById(id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    
}
