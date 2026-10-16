package com.luthier.service.controllers;

import com.luthier.service.domain.records.ReportResponses.Dashboard;
import com.luthier.service.domain.records.ReportResponses.Financial;
import com.luthier.service.services.ReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  /** Tela "Hoje". */
  @GetMapping("/dashboard")
  public Dashboard dashboard() {
    return reports.dashboard();
  }

  /** Financeiro; sem datas = mês corrente. ?from=2026-10-01&to=2026-10-31 */
  @GetMapping("/financial")
  public Financial financial(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return reports.financial(from, to);
  }
}
