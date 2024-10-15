
# Franchise Application Deployment Guide

Este documento explica cómo desplegar la aplicación en una nueva máquina, configurar la infraestructura necesaria en **AWS DynamoDB** utilizando **CloudFormation**, y configurar el servidor local para hacer uso de los endpoints con **Postman**.

---

## 1. **Requisitos Previos**
Antes de comenzar, asegúrate de tener instalados los siguientes requisitos:
- **Docker** (para contenerización y ejecutar el servicio localmente)
- **AWS CLI** instalado y configurado con las credenciales necesarias
- **Java 21** instalado
- **Postman** o cualquier otra herramienta de API
- **Git** para clonar el proyecto

---

## 2. **Clonar el Proyecto**
Clona el repositorio del proyecto desde el control de versiones:
```bash
git clone <repository-url>
cd <project-directory>
```

---

## 3. **Cargar la Infraestructura con CloudFormation**

### **a. Crear las Tablas en DynamoDB**
El proyecto requiere tres tablas principales en **DynamoDB**: `Events`, `Idempotency`, y `MaxProductPerFranchise`. Las definiciones de estas tablas están en los archivos **YAML** que se encuentran en el directorio `resources`.

#### **i. Crear las tablas `Events` y `Idempotency`**
1. Desde el root del proyecto, ejecuta el siguiente comando para crear las tablas **Events** y **Idempotency**:
   
   ```bash
   aws cloudformation create-stack --stack-name EventStoreStack    --template-body file://resources/event-store.yml    --capabilities CAPABILITY_NAMED_IAM
   ```

   - Este comando utilizará el archivo `event-store.yml` que define las tablas de eventos y control de idempotencia.

#### **ii. Crear la tabla `MaxProductPerFranchise`**
2. Luego, ejecuta el siguiente comando para crear la tabla **MaxProductPerFranchise**:
   
   ```bash
   aws cloudformation create-stack --stack-name MaxProductProjectionStack    --template-body file://resources/max-product-projection.yml    --capabilities CAPABILITY_NAMED_IAM
   ```

   - Este comando utiliza el archivo `max-product-projection.yml` que define la tabla que almacenará el estado calculado por la proyección de productos.

#### **iii. Validar las tablas creadas**
Para verificar que las tablas se han creado correctamente, puedes listar las tablas en **DynamoDB** con el siguiente comando:
```bash
aws dynamodb list-tables
```

---

## 4. **Configuración del Entorno Local con Docker**

La aplicación utiliza **Docker** para contenerización. Asegúrate de que Docker esté corriendo en tu sistema antes de seguir los pasos.

#### **i. Crear el Contenedor**
Desde el root del proyecto, ejecuta el siguiente comando para iniciar los servicios en Docker:

```bash
docker-compose up --build
```

Este comando:
- Construirá la imagen de la aplicación.
- Creará los contenedores necesarios para ejecutar la aplicación en un entorno local.

#### **ii. Verificar la Ejecución**
Después de que Docker esté corriendo, verifica que la aplicación esté funcionando accediendo a:

```
http://localhost:8080
```

---

## 5. **Interacción con Endpoints Usando Postman**

### **a. Configuración de Endpoints**
Los principales endpoints expuestos por la aplicación incluyen:

1. **Crear una franquicia**:
   - Método: `POST`
   - URL: `http://localhost:8080/api/v1/franchise/create`
   - Body (JSON):
     ```json
     {
       "franchiseName": "Starbucks",
       "franchiseId": "STB12345"
     }
     ```

2. **Agregar una sucursal a la franquicia**:
   - Método: `POST`
   - URL: `http://localhost:8080/api/v1/franchise/add-branch`
   - Body (JSON):
     ```json
     {
       "franchiseId": "STB12345",
       "branchId": "BR001",
       "branchName": "Branch 1"
     }
     ```

3. **Consulta del producto con más stock por sucursal en una franquicia**:
   - Método: `GET`
   - URL: `http://localhost:8080/api/v1/franchise/max-product?franchiseId=STB12345`

### **b. Uso de Postman**
1. Abre **Postman**. 
2. Importe la Postman Collection 
3. Abra Postman e importe el archivo de colección situado en el directorio raíz del proyecto. 
4. Utilice las solicitudes proporcionadas para probar los puntos finales de la API.

---

## 6. **Eliminación del Stack de CloudFormation**

Si deseas eliminar el stack y toda la infraestructura creada, puedes ejecutar los siguientes comandos:

```bash
aws cloudformation delete-stack --stack-name EventStoreStack
aws cloudformation delete-stack --stack-name MaxProductProjectionStack
```

---

## 7. **Consideraciones Adicionales**

- Asegúrate de que las credenciales de **AWS** estén configuradas adecuadamente. Puedes configurarlas usando:
  ```bash
  aws configure
  ```

- Verifica que los permisos necesarios estén asignados a tu usuario de **AWS** para operar con **CloudFormation**, **DynamoDB**, y **IAM**.

---

Este archivo **README** cubre los pasos necesarios para desplegar la infraestructura en **AWS**, iniciar la aplicación localmente con **Docker**, y probar los endpoints de la API usando **Postman**.
