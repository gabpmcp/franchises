# Utilizar la imagen base de Amazon Corretto (Java 21)
FROM amazoncorretto:21

# Establecer el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiar el archivo JAR generado por el build
COPY target/franchises-app.jar /app/franchises-app.jar

# Exponer el puerto en el que corre la aplicación (por ejemplo, 8080)
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "/app/franchises-app.jar"]
