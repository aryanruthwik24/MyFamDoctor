$pdfLines = @(
  '%PDF-1.4',
  '1 0 obj',
  '<< /Type /Catalog /Pages 2 0 R >>',
  'endobj',
  '2 0 obj',
  '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
  'endobj',
  '3 0 obj',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
  'endobj',
  '4 0 obj',
  '<< /Length 44 >>',
  'stream',
  'BT /F1 18 Tf 50 70 Td (Hello PDF) Tj ET',
  'endstream',
  'endobj',
  '5 0 obj',
  '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  'endobj',
  'xref',
  '0 6',
  '0000000000 65535 f ',
  '0000000010 00000 n ',
  '0000000062 00000 n ',
  '0000000123 00000 n ',
  '0000000245 00000 n ',
  '0000000456 00000 n ',
  'trailer',
  '<< /Root 1 0 R /Size 6 >>',
  'startxref',
  '520',
  '%%EOF'
)

$pdfPath = Join-Path $env:TEMP 'healthtest.pdf'
$pdfLines -join [Environment]::NewLine | Set-Content -Path $pdfPath -Encoding ascii

$register = Invoke-RestMethod -Method Post -Uri 'http://localhost:8084/api/auth/register' -ContentType 'application/json' -Body '{"fullName":"Upload Tester","email":"uploadtester@example.com","password":"Password123!"}' -ErrorAction SilentlyContinue
if ($register -and $register.token) {
  $token = $register.token
} else {
  $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8084/api/auth/login' -ContentType 'application/json' -Body '{"email":"uploadtester@example.com","password":"Password123!"}'
  $token = $login.token
}

if (-not $token) {
  throw 'No token returned from auth API.'
}

$headers = @{ Authorization = "Bearer $token" }
curl.exe -sS -X POST 'http://localhost:8084/api/reports' -H "Authorization: Bearer $token" -F "file=@$pdfPath;type=application/pdf" -F "title=Upload test" -F "reportDate=2026-10-20" -F "type=PRESCRIPTION"
Write-Output "TOKEN_OK:$($null -ne $token)"
