package com.quizapp.controllers;

import com.quizapp.dtos.CreateTestRequest;
import com.quizapp.dtos.TestResponse;
import com.quizapp.services.TestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/tests")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TestResponse> createTest(@RequestBody CreateTestRequest req) {
        TestResponse resp = testService.createTest(req);
        return ResponseEntity.created(URI.create("/api/tests/" + resp.id())).body(resp);
    }

    @GetMapping("/{id}")
    public TestResponse getTest(@PathVariable Long id) {
        return testService.getTest(id);
    }

    @PutMapping("/{id}")
    public TestResponse updateTest(@PathVariable Long id, @RequestBody CreateTestRequest req) {
        return testService.updateTest(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTest(@PathVariable Long id) {
        testService.deleteTest(id);
    }
}

