package com.ahealth.backend.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
  private final HomeService homeService;

  public HomeController(HomeService homeService) {
    this.homeService = homeService;
  }

  @GetMapping("/summary")
  public HomeDtos.HomeSummaryResponse getSummary() {
    return homeService.getSummary();
  }
}
