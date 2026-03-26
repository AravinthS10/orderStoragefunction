FROM mcr.microsoft.com/azure-functions/java:4-java17

COPY target/azure-functions/hellofunction-20260325183936527/ /home/site/wwwroot

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

EXPOSE 80