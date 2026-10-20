package com.healthtimeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {
    private final ChatService chatService;

    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    record Ask(String question) {}

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody Ask ask, Principal principal) throws Exception {
        if (ask == null || ask.question() == null || ask.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("answer", "Please enter a question."));
        }
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of("answer", "Please sign in before asking about reports."));
        }

        return chatService.ask(principal.getName(), ask.question());
    }

    @PostMapping(value = "/guest", consumes = "multipart/form-data")
    public ResponseEntity<?> askGuest(
            @RequestParam String question,
            @RequestParam("files") java.util.List<MultipartFile> files
    ) throws Exception {
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("answer", "Please enter a question."));
        }
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            return ResponseEntity.badRequest().body(Map.of("answer", "Upload at least one PDF before asking a question."));
        }

        return chatService.askGuest(question, files);
    }

    public static String buildGeminiRequest(String model, String prompt, String pdfBase64) {
        return ChatService.buildGeminiRequest(model, prompt, pdfBase64);
    }

    static String esc(String s) {
        return ChatService.esc(s);
    }

    static String extract(String json) {
        return ChatService.extract(json);
    }

    static String extractGeminiAnswer(String json) {
        return ChatService.extractGeminiAnswer(json);
    }
}

