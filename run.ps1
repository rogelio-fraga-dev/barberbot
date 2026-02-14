# Script para carregar variaveis de ambiente do arquivo .env e executar a aplicacao

if (Test-Path .env) {
    Get-Content .env -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
            Write-Host "Carregado: $key" -ForegroundColor Green
        }
    }
} else {
    Write-Host "Arquivo .env nao encontrado!" -ForegroundColor Yellow
}

if (-not $env:OPENAI_API_KEY) {
    Write-Host "ERRO: OPENAI_API_KEY nao esta definida!" -ForegroundColor Red
    Write-Host "Defina a variavel no arquivo .env" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Iniciando BarberBot Assist..." -ForegroundColor Cyan
Write-Host ""

# Usa Maven Wrapper (mvnw.cmd) se existir; senao usa mvn do PATH
$mvnCmd = $null
if (Test-Path ".\mvnw.cmd") {
    $mvnCmd = ".\mvnw.cmd"
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnCmd = "mvn"
}

if ($mvnCmd) {
    & $mvnCmd spring-boot:run
} else {
    Write-Host "Maven nao encontrado." -ForegroundColor Red
    Write-Host ""
    Write-Host "Opcoes para rodar a aplicacao:" -ForegroundColor Yellow
    Write-Host "1. Pela IDE: abra BarberBotApplication.java e execute o metodo main (Run)" -ForegroundColor White
    Write-Host "2. Instale o Maven e adicione ao PATH: https://maven.apache.org/download.cgi" -ForegroundColor White
    Write-Host "3. Se tiver winget: winget install Apache.Maven" -ForegroundColor White
    Write-Host ""
    exit 1
}
