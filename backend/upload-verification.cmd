@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "PDF_FILE=%TEMP%\healthtest.pdf"
> "%PDF_FILE%" (
  echo %PDF-1.4
  echo 1 0 obj
  echo << /Type /Catalog /Pages 2 0 R >>
  echo endobj
  echo 2 0 obj
  echo << /Type /Pages /Kids [3 0 R] /Count 1 >>
  echo endobj
  echo 3 0 obj
  echo << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
  echo endobj
  echo 4 0 obj
  echo << /Length 44 >>
  echo stream
  echo BT /F1 18 Tf 50 70 Td (Hello PDF) Tj ET
  echo endstream
  echo endobj
  echo 5 0 obj
  echo << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
  echo endobj
  echo xref
  echo 0 6
  echo 0000000000 65535 f 
  echo 0000000010 00000 n 
  echo 0000000062 00000 n 
  echo 0000000123 00000 n 
  echo 0000000245 00000 n 
  echo 0000000456 00000 n 
  echo trailer
  echo << /Root 1 0 R /Size 6 >>
  echo startxref
  echo 520
  echo %%EOF
)

curl.exe -s -X POST http://localhost:8084/api/auth/register -H "Content-Type: application/json" -d "{\"fullName\":\"Upload Tester\",\"email\":\"uploadtester@example.com\",\"password\":\"Password123!\"}" > "%TEMP%\register.json"
findstr /C:"token" "%TEMP%\register.json" >nul
if not errorlevel 1 (
  for /f "tokens=2 delims=:,}" %%A in ('findstr /R /C:"\"token\"" "%TEMP%\register.json"') do set "TOKEN=%%~A"
) else (
  curl.exe -s -X POST http://localhost:8084/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"uploadtester@example.com\",\"password\":\"Password123!\"}" > "%TEMP%\login.json"
  for /f "tokens=2 delims=:,}" %%A in ('findstr /R /C:"\"token\"" "%TEMP%\login.json"') do set "TOKEN=%%~A"
)

if not defined TOKEN (
  echo TOKEN_NOT_FOUND
  exit /b 1
)

curl.exe -s -X POST http://localhost:8084/api/reports -H "Authorization: Bearer !TOKEN!" -F "file=@%PDF_FILE%;type=application/pdf" -F "title=Upload test" -F "reportDate=2026-10-20" -F "type=PRESCRIPTION"

echo.
echo TOKEN_OK
