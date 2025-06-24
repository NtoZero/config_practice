<#
 create-keystore-v5.ps1
#>

# 에러가 나면 즉시 중단
$ErrorActionPreference = 'Stop'

Write-Host "Starting PKCS#12 keystore creation..."

# 1) 32바이트 랜덤 바이트 생성
$bytes = New-Object byte[] 32

# PowerShell 5.1/.NET Framework 호환:
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()

# 2) Base64로 비밀번호 변환
$STOREPASS = [Convert]::ToBase64String($bytes)

# 3) keytool 호출
$keyStoreFile = "keystore.p12"
$keyAlias     = "jasypt-secret-key"

& keytool -genseckey `
    -alias     $keyAlias `
    -keyalg    AES `
    -keysize   256 `
    -storetype PKCS12 `
    -keystore  $keyStoreFile `
    -storepass $STOREPASS `
    -keypass   $STOREPASS

# 4) 비밀번호 파일 저장
$passFile = "keystore_pass.txt"
$STOREPASS | Out-File -FilePath $passFile -Encoding ascii

# 5) 민감 변수 삭제
Remove-Variable bytes, STOREPASS, rng

# 완료 안내
Write-Host ""
Write-Host "✅ Keystore generated: $keyStoreFile"
Write-Host "🔑 Password saved to: $passFile"
Write-Host ""
Write-Host 'Please set environment variable before running your app:'
Write-Host "  `$env:JASYPT_STOREPASS = Get-Content $passFile"
