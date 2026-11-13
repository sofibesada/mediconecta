# ADR 0008 · Singleton y tareas programadas (@Schedule)

- **Estado:** aceptada
- **Fecha:** 13/11/2026
- **Grupo 4:** Besada, Lorenzo, Navello, Torres

## Contexto

Hay dos tareas que no las dispara ningún usuario:

1. **Crear la cuenta del administrador** la primera vez que se despliega. El registro
   público solo crea pacientes, así que alguien tiene que existir para dar de alta a los
   profesionales.
2. **Enviar los recordatorios** de los turnos del día siguiente.

Las dos tienen que correr **una sola vez** por momento: no pueden haber dos instancias
creando el administrador ni dos corridas enviando el mismo recordatorio.

## Decisión

Usamos EJB `@Singleton`, una sola instancia por aplicación, con acceso serializado por el
contenedor:

- **`SeedAdmin`** (`@Singleton @Startup`): en el `@PostConstruct` crea la cuenta ADMIN si
  no existe. La contraseña **no está en el código**: se lee de
  `MEDICONECTA_ADMIN_PASSWORD` (o `mediconecta.admin.password`). Si no está configurada, no
  se crea nada y se avisa en el log.
- **`RecordatorioTurnos`** (`@Singleton`, con `@Schedule(minute = "*", persistent = false)`):
  el TimerService del contenedor lo ejecuta cada minuto.
  - Busca los turnos confirmados de las próximas 24 horas (configurable con
    `mediconecta.recordatorio.horas`) que todavía no tienen recordatorio.
  - Los marca (`recordatorioEnviadoPara`) y publica `TURNO_RECORDATORIO` en el tópico de
    eventos (ADR 0005). La marca y la publicación van en la **misma transacción**: si falla
    el envío, el turno no queda marcado y se reintenta en la corrida siguiente.
  - `persistent = false`: si el servidor estuvo apagado, no se recuperan las corridas
    perdidas. No hace falta, porque cada corrida busca por ventana de tiempo.

## Alternativas consideradas

- **Un script SQL para el administrador.** Habría que guardar el hash de la contraseña en
  el repositorio.
- **Un cron externo que llame a la API.** Agrega otra pieza que operar y una credencial
  más.
- **Un timer persistente.** Recupera corridas perdidas, pero no aporta nada a una búsqueda
  por ventana y deja timers guardados entre despliegues.

## Consecuencias

- Al levantar con Docker ya existe el administrador del entorno de demo.
- Cada turno recibe **un** recordatorio. Si el mismo evento se entregara dos veces, el
  consumidor idempotente evita el aviso duplicado (ADR 0005).
- Con varias instancias de WildFly en cluster, cada una tendría su propio `@Singleton`. Hoy
  hay una sola; para escalar habría que usar un timer de cluster o un lock en la base.

## Cómo se verifica

- `RecordatoriosYAgendaTest`: cada turno próximo recibe un recordatorio y queda marcado, y
  la ventana es de 24 horas salvo que se configure otra.
- `UsuarioServiceTest`: sembrar el administrador es idempotente.
- En el log de WildFly aparece `[Timer @Schedule] ... envio N recordatorio(s)`.
