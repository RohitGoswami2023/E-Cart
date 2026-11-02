# PowerShell helper to run the CSV import
# -------------------------------------------------
# Usage:
#   .\scripts\import-data.ps1
# This script starts the Spring Boot application with the special flag
# that triggers the DataImporter (see backend/src/main/java/com/ecart/util/DataImporter.java).
# Ensure you have Maven wrapper (mvnw) executable permissions.

Set-Location "$PSScriptRoot/../backend"
# Use the Maven wrapper to run the app with the import flag
./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--import-data

