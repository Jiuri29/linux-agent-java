package com.linux.agent.controller;

import com.linux.agent.service.FileService;
import com.linux.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping({"/api/files/list", "/api/v1/files/list"})
    public Object list(@RequestParam(value = "path", required = false) String path,
                       HttpServletRequest request) {
        Object result = fileService.list(path);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping("/api/files/read")
    public Map<String, Object> read(@RequestParam("path") String path) {
        return fileService.read(path);
    }

    @GetMapping("/api/files/download")
    public ResponseEntity<byte[]> download(@RequestParam("path") String path) {
        byte[] bytes = fileService.download(path);
        String fileName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping("/api/files/write")
    public Map<String, Object> write(@RequestBody Map<String, Object> body) {
        Object content = body.getOrDefault("content", "");
        return fileService.write(String.valueOf(body.get("path")),
                String.valueOf(content).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/api/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("path") String path) throws java.io.IOException {
        return fileService.write(path, file.getBytes());
    }

    @PostMapping("/api/files/delete")
    public Map<String, Object> delete(@RequestBody Map<String, Object> body) {
        return fileService.delete(String.valueOf(body.get("path")));
    }

    @GetMapping("/api/files/backups")
    public Object backups(@RequestParam(value = "path", required = false) String path,
                          HttpServletRequest request) {
        Object result = fileService.backups(path);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping("/api/files/diff")
    public Map<String, Object> diff(@RequestParam("path") String path,
                                    @RequestParam(value = "backup_id", required = false) String backupId) {
        return fileService.diff(path, backupId);
    }

    @PostMapping("/api/files/restore")
    public Map<String, Object> restore(@RequestBody Map<String, Object> body) {
        return fileService.restore(body);
    }

    @PostMapping("/api/files/operation/prepare")
    public Map<String, Object> prepare(@RequestBody Map<String, Object> body) {
        return fileService.prepareOperation(body);
    }
}
