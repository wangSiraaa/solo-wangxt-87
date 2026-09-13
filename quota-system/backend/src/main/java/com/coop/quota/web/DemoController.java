package com.coop.quota.web;

import com.coop.quota.service.DemoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    /** 一键运行样例验证场景，返回逐步日志 */
    @PostMapping("/run")
    public Map<String, Object> run() {
        return Map.of("log", demoService.runScenarios());
    }
}
