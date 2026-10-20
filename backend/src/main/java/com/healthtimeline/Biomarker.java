package com.healthtimeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;

public class Biomarker {
  public Long id;
  public BiomarkerName name;
  public BigDecimal value;
  public String unit;
  public String referenceRange;
  public String qualifier;
  @JsonIgnore public Report report;
}
