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

## Acción Requerida
El usuario debe aplicar esta configuración manualmente en su cuenta de [UptimeRobot](https://uptimerobot.com/).
