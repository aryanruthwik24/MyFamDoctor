export type Marker = {
  name: string;
  value: string;
  unit?: string;
  qualifier?: string;
  referenceRange?: string;
};

export type LabValue = {
  test_name?: string;
  value_num?: number | null;
  value_text?: string | null;
  unit?: string | null;
  ref_low?: number | null;
  ref_high?: number | null;
  flag?: string | null;
  summary?: string | null;
  test_name_summary?: string | null;
  value_summary?: string | null;
};

export type Report = {
  id: number;
  title: string;
  reportDate: string;
  type: string;
  fileName: string;
  createdAt: string;
  extractedText?: string;
  fileUrl?: string;
  sourceFile?: File;
  mimeType?: string;
  biomarkers: Marker[];
  values?: LabValue[];
};

export type Message = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
};

export type View = 'home' | 'reports';

export type ReportLike = Pick<
  Report,
  'title' | 'reportDate' | 'type' | 'extractedText' | 'biomarkers' | 'fileName' | 'createdAt' | 'fileUrl' | 'mimeType' | 'values'
>;
