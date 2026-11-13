# ADR 0010 · Arquitectura en capas con Repository y DTO

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

MediConecta tiene varios componentes de negocio: usuarios, turnos, historia clínica,
notificaciones, obras sociales, telemedicina y facturación. Cada uno recibe pedidos REST,
aplica reglas y guarda datos.

Si los recursos REST hicieran consultas JPA o devolvieran las entidades tal cual, el
contrato de la API quedaría atado al modelo de la base. Además, podrían filtrarse datos
sensibles, como el hash de la contraseña.

## Decisión

Cada componente se organiza en el mismo conjunto de paquetes:

| Paquete | Responsabilidad | Ejemplo |
|---|---|---|
| `presentation` | Recursos JAX-RS y **DTO** de entrada y salida | `TurnoResource`, `TurnoResponse`, `CrearTurnoRequest` |
| `business` | Reglas de negocio (EJB) detrás de una interfaz | `ITurnoService` / `TurnoService` |
| `data` | **Repository**: el único que usa el `EntityManager` y JPQL | `TurnoRepository` |
| `model` | Entidades JPA y estados | `Turno`, `EstadoTurno` |
| `integracion` / `messaging` | Clientes externos y JMS | `ObrasSocialesClient`, `FacturacionMDB` |

- **Repository:** los servicios nunca escriben consultas; dependen del repositorio. Un
  cambio en el modelo de persistencia queda aislado ahí.
- **DTO:** la API recibe y devuelve records propios. `UsuarioResponse` omite el hash de la
  contraseña. `TurnoResponse` agrega el nombre del profesional y del paciente para que el
  frontend no tenga que buscarlos.
- **Interfaces de negocio** (`I...Service`): la presentación y otros componentes dependen de
  la interfaz, no de la implementación. Por eso se pueden reemplazar con mocks en las
  pruebas.
- **Seguridad en la presentación:** el id del usuario sale del token JWT, nunca de un
  parámetro. Los recursos ajenos responden 404 (`UsuarioActual.exigir`).

## Alternativas consideradas

- **Exponer las entidades JPA.** Es menos código, pero filtra campos internos y rompe la
  API con cada cambio de la base.
- **Active Record** (la entidad se guarda a sí misma). Mezcla persistencia y dominio y no
  encaja con los EJB y el `EntityManager` del contenedor.

## Consecuencias

- La cobertura de pruebas se mide sobre la capa de negocio, que es donde están las reglas:
  80 % de líneas y 70 % de ramas como mínimo (JaCoCo, ver `pom.xml`).
- Hay algo de código repetido entre entidades y DTO. Es el costo de mantener el contrato
  estable.

## Cómo se verifica

- Las pruebas unitarias de `business` usan mocks de los repositorios (`*ServiceTest`).
- `UsuarioServiceTest.creaUnPacienteConLaContrasenaHasheada`: la contraseña se guarda con
  hash BCrypt.
- `SeguridadApiTest`: sin token responde 401, con un rol que no corresponde 403, y los
  recursos de otro usuario 404 sin devolver datos.
