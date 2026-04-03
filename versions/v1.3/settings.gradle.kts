rootProject.name = "infra-monitor"

include(
    "metrics-service",
    "alert-service",
    "dashboard-service",
    "node-agent",
    "common-api",
    "common-events"
)