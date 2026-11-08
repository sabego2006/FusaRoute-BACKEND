# Guía de Monitoreo de Disponibilidad - FusaRoute Backend

Para cumplir con el requisito de disponibilidad ($\ge 95\%$) de SCRUM-128, se debe configurar el monitoreo externo mediante **UptimeRobot**.

## Configuración de UptimeRobot

Cree un nuevo monitor con los siguientes parámetros:

| Campo | Valor | Notas |
| :--- | :--- | :--- |
| **Monitor Type** | HTTP(s) | Monitoreo estándar de endpoint |
| **Friendly Name** | FusaRoute Backend Health | Identificador del servicio |
| **URL (or IP)** | `https://fusaroute-backend.onrender.com/health` | Endpoint público de salud |
| **Monitoring Interval** | 5 minutes | Intervalo mínimo recomendado en plan gratuito |
| **Timeout** | 30 seconds | **Crítico**: Render tiene "cold start" en tier gratuito (~60s) |

## Verificación
El monitor debe marcarse como **UP** siempre que el endpoint responda con un código HTTP `200 OK`.

## Decisión sobre el cold start (2026-11-08)

El tier gratis de Render duerme el servicio tras ~15 min sin tráfico y el siguiente
usuario espera hasta ~60 s. Es incompatible con `LCP < 2.5 s` (RNF-01) y ninguna
optimización de payload o de caché lo arregla. Opciones evaluadas: ping de calentamiento
($0), plan pago de Render (~7 USD/mes) o declarar el RNF "con el servicio caliente".

**Decisión: ping de calentamiento.** `.github/workflows/keep-warm.yml` hace `GET /health`
cada 10 min entre las 06:00 y las 23:00 (hora de Bogotá). UptimeRobot (arriba) también
consulta `/health` cada 5 min, pero es un paso manual que este repo no puede verificar:
no se cuenta con él para mantener el servicio despierto.

**Es un parche, y así se declara ante el comité.** Lo que no cubre:

- El primer request tras un despliegue o un reinicio sigue siendo en frío.
- Fuera de la ventana 06:00–23:00 el servicio puede dormirse.
- GitHub Actions no garantiza la puntualidad del `schedule`: bajo carga puede retrasar o
  saltarse ejecuciones, y en repositorios públicos desactiva los workflows programados
  tras 60 días sin actividad. Si falla, el run queda en rojo y llega un correo.
- Mantener el servicio despierto consume las 750 h/mes gratis de Render (~510 h con esta
  ventana). Otro servicio en la misma cuenta resta de ese cupo.

El LCP de 2.5 s se cumple con el servicio caliente. Con el servicio dormido no, y la
evidencia de RNF-01 debe decirlo así en vez de omitirlo.

## Acción Requerida
El usuario debe aplicar esta configuración manualmente en su cuenta de [UptimeRobot](https://uptimerobot.com/).
