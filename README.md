# 🏥 Sistema de Administración de Citas (Consultorio Clínico)

Este proyecto es una simulación de un sistema de administración de citas para un consultorio clínico.  
Permite gestionar doctores, pacientes, citas y administradores con persistencia en archivos **CSV**.

---

## ⚙️ Instalación y configuración

1. **Requisitos previos**
   - Tener instalado **Java 17** o superior.
   - Tener configurado el **JDK** en tu IDE (IntelliJ, Eclipse, VS Code, etc.).
   - Tener configurada la variable de entorno `JAVA_HOME`.
   
   Configuración de datos

El sistema utiliza archivos CSV ubicados en la carpeta data/:

doctores.csv

pacientes.csv

citas.csv

admins.csv

Los archivos incluyen encabezados y se crean automáticamente si no existen.

🚀 Uso del programa

Inicio de sesión

Solo administradores registrados en admins.csv pueden acceder al sistema.

Cada administrador requiere un ID y contraseña encriptada.

Opciones disponibles

Dar de alta doctores.

Dar de alta pacientes.

Crear una cita (fecha, hora y motivo).

Relacionar cita con un doctor y un paciente.

Listar, buscar y exportar citas.

Guardar información en archivos CSV.

Persistencia de datos

Los datos se almacenan en archivos CSV para garantizar portabilidad.

El sistema maneja copias de respaldo (.bak) al guardar cambios.

👥 Créditos

Proyecto desarrollado por:

Gustavo Carmona Olivares

📄 Licencia

Este proyecto está bajo la licencia MIT.