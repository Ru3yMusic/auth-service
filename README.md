# auth-service

Microservicio de autenticación y gestión de usuarios de **RUBY MUSIC**. Maneja registro por email (con OTP), login, OAuth Google, emisión de JWT RS256, refresh tokens y recuperación de contraseña.

---

## Responsabilidad

- Registro multi-step con verificación de email por OTP (5 dígitos, 10 min de validez)
- Login con email/contraseña y Google OAuth
- Emisión de pares JWT (access token RS256 15 min + refresh token 30 días)
- Gestión de sesiones por dispositivo con revocación individual o total
- Recuperación de contraseña por OTP
- CRUD parcial de perfil de usuario (nombre + foto)

---

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.1 |
| Spring Security | — (stateless, CSRF off) |
| Spring Data JPA | — |
| Spring Mail | — |
| JJWT | 0.12.5 |
| MapStruct | — |
| Lombok | — |
| SpringDoc OpenAPI | — |
| OpenAPI Generator (Maven plugin) | — |

---

## Puerto

| Servicio | Puerto |
|---|---|
| auth-service | **8081** |
| Acceso vía gateway | `http://localhost:8080/api/v1/auth` |

---

## Base de datos

| Parámetro | Valor |
|---|---|
| Engine | PostgreSQL |
| Database | `auth_db` |
| Host | `localhost:5432` |
| DDL | `update` (Hibernate auto-schema) |

### Entidades

| Tabla | Descripción |
|---|---|
| `users` | Perfil completo, provider, estado de verificación |
| `email_verifications` | OTPs de registro y password reset (5 dígitos, TTL 10 min) |
| `refresh_tokens` | Sesiones activas por dispositivo (hash SHA-256) |

---

## Endpoints

La configuración completa está en `src/main/resources/openapi.yml`. Los controllers implementan las interfaces generadas por el plugin OpenAPI Generator.

### Registro (`/api/v1/auth`)

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/register` | Crear usuario + enviar OTP | No |
| `POST` | `/verify-email` | Validar OTP de registro | No |
| `POST` | `/resend-otp` | Reenviar OTP | No |

### Autenticación

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/login` | Login email + password | No |
| `POST` | `/login/google` | Login OAuth Google *(pendiente)* | No |
| `POST` | `/refresh` | Renovar access token | No |
| `POST` | `/logout` | Revocar refresh token actual | No |
| `POST` | `/logout-all` | Revocar todas las sesiones | `X-User-Id` header |

### Contraseña

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/password/reset-request` | Solicitar OTP de reset (silencioso en emails desconocidos) | No |
| `POST` | `/password/reset` | Aplicar nuevo password con OTP | No |

### Usuarios

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/users/{id}` | Obtener perfil de usuario | No |
| `PATCH` | `/users/{id}/profile` | Actualizar nombre y/o foto | No |

---

## Modelo de seguridad JWT

### Algoritmo: RS256 (RSA + SHA-256)

```
auth-service          →  firma con  →  PRIVATE KEY  →  genera access token
api-gateway / clientes →  valida con →  PUBLIC KEY   →  verifica firma
```

### Claims del access token

| Claim | Valor |
|---|---|
| `sub` | UUID del usuario |
| `email` | Email registrado |
| `displayName` | Nombre para mostrar |
| `profilePhotoUrl` | URL de foto de perfil |
| Expiración | 15 minutos |

### Refresh token

- UUID aleatorio generado en servidor
- Almacenado como **hash SHA-256** en `refresh_tokens` (nunca en texto plano)
- Validez: 30 días
- Soporte multi-dispositivo: cada login crea un registro independiente
- Revocación: individual (logout) o total (logout-all / reset password)

### Contraseñas

- Almacenadas con **BCrypt**
- `passwordHash` es `NULLABLE` (usuarios OAuth no tienen contraseña)

---

## Flujos principales

### Registro por email
```
POST /register  →  crear User (no verificado)  →  generar OTP  →  enviar email
POST /verify-email  →  validar OTP  →  marcar is_email_verified = true
```

### Login
```
POST /login  →  verificar email + bcrypt  →  crear RefreshToken (hash)  →  emitir TokenPair
```

### Refresh token
```
POST /refresh  →  SHA-256(rawToken)  →  lookup en DB  →  validar expiración y revocación  →  nuevo access token
```

### Reset de contraseña
```
POST /password/reset-request  →  generar OTP tipo PASSWORD_RESET  →  enviar email (silencioso si no existe)
POST /password/reset          →  validar OTP  →  bcrypt nuevo password  →  revocar TODAS las sesiones
```

---

## Variables de entorno

| Variable | Descripción | Default |
|---|---|---|
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña PostgreSQL | `password` |
| `MAIL_USERNAME` | Cuenta Gmail para OTP | — |
| `MAIL_PASSWORD` | App password de Gmail | — |
| `JWT_PRIVATE_KEY` | Clave RSA privada PKCS#8 en Base64 | — |
| `JWT_PUBLIC_KEY` | Clave RSA pública X.509 en Base64 | — |

### Generar par de claves RSA (una sola vez)

```bash
# Generar clave privada PKCS#8
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048

# Extraer clave pública X.509
openssl rsa -pubout -in private_key.pem -out public_key.pem

# Obtener Base64 sin headers PEM (para las variables de entorno)
grep -v "^-----" private_key.pem | tr -d '\n'   # → JWT_PRIVATE_KEY
grep -v "^-----" public_key.pem  | tr -d '\n'   # → JWT_PUBLIC_KEY
```

> La clave pública (`JWT_PUBLIC_KEY`) también debe configurarse en el `api-gateway` para que pueda validar los tokens.

---

## Estructura del proyecto

```
auth-service/
├── src/
│   ├── main/
│   │   ├── java/com/rubymusic/auth/
│   │   │   ├── AuthServiceApplication.java
│   │   │   ├── config/
│   │   │   │   ├── JwtConfig.java           ← carga beans PrivateKey / PublicKey
│   │   │   │   ├── JwtProperties.java       ← @ConfigurationProperties jwt.*
│   │   │   │   └── SecurityConfig.java      ← stateless, BCrypt bean
│   │   │   ├── controller/
│   │   │   │   ├── AuthenticationController.java
│   │   │   │   ├── PasswordController.java
│   │   │   │   ├── RegistrationController.java
│   │   │   │   └── UsersController.java
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java   ← manejo centralizado de errores
│   │   │   ├── mapper/
│   │   │   │   └── UserMapper.java               ← MapStruct: User → UserResponse
│   │   │   ├── model/
│   │   │   │   ├── User.java
│   │   │   │   ├── EmailVerification.java
│   │   │   │   ├── RefreshToken.java
│   │   │   │   └── enums/
│   │   │   │       ├── AuthProvider.java          ← EMAIL | GOOGLE
│   │   │   │       ├── Gender.java                ← FEMENINO | MASCULINO | NO_BINARIO | OTRO | PREFER_NOT_SAY
│   │   │   │       └── VerificationType.java      ← REGISTER | PASSWORD_RESET
│   │   │   ├── repository/
│   │   │   │   ├── EmailVerificationRepository.java
│   │   │   │   ├── RefreshTokenRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       ├── AuthService.java               ← interfaz con contratos
│   │   │       ├── TokenPair.java                 ← record: accessToken, refreshToken, expiresInMs
│   │   │       ├── UserService.java
│   │   │       └── impl/
│   │   │           ├── AuthServiceImpl.java
│   │   │           └── UserServiceImpl.java
│   │   └── resources/
│   │       ├── application.yml                    ← nombre + import config-server
│   │       └── openapi.yml                        ← contrato OpenAPI 3.0.3 completo
│   └── test/
│       └── java/com/rubymusic/auth/
│           └── AuthServiceApplicationTests.java
└── pom.xml
```

### Clases generadas por OpenAPI Generator

El plugin Maven `openapi-generator-maven-plugin` genera en `target/generated-sources/`:
- Interfaces de controller (`RegistrationApi`, `AuthenticationApi`, `PasswordApi`, `UsersApi`)
- DTOs de request y response

Los controllers del proyecto implementan estas interfaces.

---

## Manejo de errores

| Excepción | HTTP |
|---|---|
| `NoSuchElementException` | `404 Not Found` |
| `IllegalArgumentException` | `400 Bad Request` |
| `IllegalStateException` | `400 Bad Request` |
| `UnsupportedOperationException` | `501 Not Implemented` |
| `DataIntegrityViolationException` | `409 Conflict` |
| `MethodArgumentNotValidException` | `422 Unprocessable Entity` (con detalle de campos) |
| `Exception` (genérico) | `500 Internal Server Error` |

---

## Build & Run

```bash
# Build (genera DTOs desde openapi.yml)
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test -Dtest=AuthServiceApplicationTests
```

> Requiere `discovery-service` y `config-server` corriendo, y PostgreSQL en `localhost:5432` con la base de datos `auth_db` creada.
