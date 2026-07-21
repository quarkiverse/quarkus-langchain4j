# A2A Agent Discovery via Apicurio Registry

Demonstrates dynamic agent discovery using Apicurio Registry as the agent card store.

A support ticket router classifies incoming requests and delegates to specialized agents
(billing, technical, shipping) discovered from the registry at runtime. The specialist
agents are referenced via `@RegistryAgent` — no hardcoded URLs or compile-time dependencies.

## Architecture

```
Customer Request
       │
       ▼
┌──────────────┐     ┌───────────────────┐
│  Classifier  │────▶│ Apicurio Registry │
│  (local LLM) │     │  ┌─────────────┐  │
└──────────────┘     │  │billing-agent│  │
       │             │  │tech-agent   │  │
       ▼             │  │ship-agent   │  │
┌──────────────┐     │  └─────────────┘  │
│ @RegistryAgent│◀───│   AGENT_CARD      │
│  (A2A client) │     └───────────────────┘
└──────────────┘
```

## Prerequisites

- Ollama running locally (dev services will start it if not)
- Apicurio Registry at `http://localhost:8080`

## Running

```bash
# Start Apicurio Registry
docker run -d -p 8080:8080 quay.io/apicurio/apicurio-registry:latest-snapshot

# Register sample agent cards
curl -X POST http://localhost:8080/apis/registry/v3/groups/default/artifacts \
  -H 'Content-Type: application/json' \
  -d '{"artifactId":"billing-agent","artifactType":"AGENT_CARD","name":"Billing Agent","description":"Handles billing inquiries, refunds, and payment issues","labels":{"a2a-agent-url":"http://localhost:9001/a2a"},"firstVersion":{"version":"1.0.0","content":{"contentType":"application/json","content":"{\"name\":\"Billing Agent\",\"description\":\"Handles billing inquiries\",\"url\":\"http://localhost:9001/a2a\",\"version\":\"1.0.0\"}"}}}'

curl -X POST http://localhost:8080/apis/registry/v3/groups/default/artifacts \
  -H 'Content-Type: application/json' \
  -d '{"artifactId":"technical-agent","artifactType":"AGENT_CARD","name":"Technical Agent","description":"Handles technical support, troubleshooting, and bug reports","labels":{"a2a-agent-url":"http://localhost:9002/a2a"},"firstVersion":{"version":"1.0.0","content":{"contentType":"application/json","content":"{\"name\":\"Technical Agent\",\"description\":\"Handles technical support\",\"url\":\"http://localhost:9002/a2a\",\"version\":\"1.0.0\"}"}}}'

# Run the sample
mvn quarkus:dev -Dquarkus.http.port=8090
```

## Endpoints

- `GET /support/agents` — lists all agents discovered from the registry
- `GET /support/classify?request=...` — classifies a support request
- `POST /support/ticket?request=...` — classifies and routes to the appropriate agent
