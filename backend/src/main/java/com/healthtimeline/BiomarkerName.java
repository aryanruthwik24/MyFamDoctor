package com.healthtimeline;

public enum BiomarkerName {
  HEMOGLOBIN("Hemoglobin"), HBA1C("HbA1c"), VITAMIN_B12("Vitamin B12"), VITAMIN_D("Vitamin D"),
  CREATININE("Creatinine"), EGFR("eGFR"), CHOLESTEROL("Cholesterol"), PLATELET_COUNT("Platelet count"),
  LDL("LDL"), HDL("HDL");
  public final String label;
  BiomarkerName(String label) { this.label = label; }
}
