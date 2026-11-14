# ADR 0014 · Cifrado de los datos clínicos en la base

- **Estado:** aceptada
- **Fecha:** 14/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

La revisión de seguridad (SEC-10) pidió evidencia de que los datos clínicos están cifrados
cuando están guardados. No la había: los diagnósticos y las recetas estaban en texto plano en
PostgreSQL. Cualquiera con acceso a la base, a un volcado o a un respaldo podía leerlos, sin
pasar por el login, los roles ni la auditoría de la aplicación (SEC-09).

## Decisión

Ciframos en la **aplicación**, campo por campo, con **AES-256-GCM**:

| Pieza | Qué hace |
|---|---|
| `CifradoDatos` | Cifra y descifra. Formato `v1:` + Base64(IV aleatorio de 12 bytes + texto cifrado + tag de 16 bytes) |
| `CampoClinicoCifrado` | `AttributeConverter` de JPA: cifra al guardar y descifra al leer. El resto del código no cambia |
| Campos cifrados | `Diagnostico.descripcion`, `Receta.medicamento` y `Receta.indicaciones` |
| `MigracionCifradoClinico` | Al desplegar, agranda las columnas y cifra lo que haya quedado en texto plano. Es idempotente |

- **GCM además autentica**: si alguien modifica un dato en la base, no se descifra («la clave no
  coincide o el dato fue modificado») en lugar de mostrar algo alterado.
- **IV aleatorio por dato**: dos diagnósticos iguales no se ven iguales en la base.
- **La clave no está en el repositorio**: `MEDICONECTA_CLAVE_DATOS` (32 bytes en Base64), en
  `.env` local; en CI se genera al azar en cada corrida.
- **Falla cerrado**: sin clave, el despliegue falla. Nunca se guardan datos clínicos sin cifrar.
- El prefijo `v1:` deja lugar para rotar la clave más adelante (`v2:` con otra clave).

## Alternativas consideradas

- **Cifrado de disco** (BitLocker, LUKS o el del proveedor de la nube): no requiere código,
  pero solo protege si se roban el disco apagado. Con la base andando, un volcado o una
  consulta directa ven todo. Es complementario, no reemplaza esta decisión.
- **`pgcrypto` en PostgreSQL**: la clave tendría que viajar en cada consulta SQL y podría
  quedar en los logs de la base.
- **Cifrar toda la ficha** (nombre, email, afiliado): esos datos sirven para buscar y para
  iniciar sesión; cifrarlos obliga a usar índices ciegos. Queda fuera de alcance.

## Consecuencias

- Un volcado o un respaldo de la base ya no expone diagnósticos ni recetas, siempre que la
  clave se guarde aparte.
- **Si se pierde la clave, los datos cifrados no se recuperan.** Hay que guardarla fuera del
  servidor (gestor de secretos o bóveda).
- No se puede buscar por el contenido de esos campos en SQL. Hoy no se usa.
- Se agregaron largos máximos (diagnóstico 1000, medicamento 200, indicaciones 1000
  caracteres). Antes, un texto de más de 255 terminaba en un error 500.
- Pruebas: `CifradoDatosTest`, `MigracionCifradoServiceTest`, `HistoriaClinicaServiceTest`.

## Respaldos

`scripts/respaldo.sh` hace un `pg_dump` dentro del contenedor y lo cifra al vuelo con AES-256
(`openssl`, clave derivada con PBKDF2 de `MEDICONECTA_CLAVE_RESPALDOS`, distinta de la de
datos). El volcado nunca toca el disco sin cifrar. Al lado deja un HMAC-SHA256:
`scripts/restaurar.sh` lo verifica antes de descifrar y rechaza un respaldo modificado o una
clave equivocada. CI hace un respaldo, comprueba que no se lee y lo restaura en una base aparte.

Así quedan protegidos también los datos que no se cifran por campo (nombres, emails, afiliados).

**Pendiente (infraestructura, no código):** programar el respaldo (cron o el Programador de
tareas), guardar las copias fuera del servidor y cifrar el disco del servidor de producción.
