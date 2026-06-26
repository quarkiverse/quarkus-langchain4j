# Workload Identity Federation with Kubernetes Projected Tokens

A Quarkus chat application that authenticates to Anthropic Claude using [Workload Identity Federation](https://docs.anthropic.com/en/docs/build-with-claude/workload-identity-federation) with [Kubernetes Identity Provider](https://platform.claude.com/docs/en/manage-claude/wif-providers/kubernetes) projected service account tokens.

No API keys are stored or distributed — the workload proves its identity through a Kubernetes-issued JWT token, which is exchanged for a short-lived Anthropic access token.

Unlike the `workload-identity-federation-spiffe` sample, this approach uses Kubernetes-native token projection and does not require SPIRE or any additional identity infrastructure — making it a good fit for single-cluster deployments. For multi-cluster or hybrid environments where a unified identity layer is needed, see the `workload-identity-federation-spiffe` sample.

## Prerequisites

- A Kubernetes or OpenShift cluster
- `kubectl` (or `oc` for OpenShift)
- An Anthropic organization with Workload Identity Federation access
- Maven and JDK 17+

## 1. Identify the cluster issuer URL and subject

When configuring Anthropic (next step), you need two values from the Kubernetes service account token:

- **Issuer URL** (`iss`): the cluster's service account issuer. Retrieve it with:

```bash
kubectl get --raw /.well-known/openid-configuration | jq -r .issuer
```

- **Subject** (`sub`): follows the format `system:serviceaccount:<namespace>:<service-account-name>`. For this sample: `system:serviceaccount:workload-identity-k8s:workload-identity-federation-kubernetes`

If the issuer URL is not publicly reachable (common with self-managed clusters), you will need to register it with Anthropic in `inline` JWKS mode. Fetch the cluster's signing keys:

```bash
kubectl get --raw /openid/v1/jwks
```

You can also verify these values by decoding a token from a running pod:

```bash
oc exec deployment/workload-identity-federation-kubernetes -n workload-identity-k8s -c app \
    -- cat /var/run/secrets/tokens/kubernetes-service-token | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```

## 2. Configure Anthropic

Follow the [Anthropic Kubernetes WIF setup guide](https://platform.claude.com/docs/en/manage-claude/wif-providers/kubernetes) to:

1. Register a **federation issuer** using the cluster issuer URL from step 1. If the issuer is not publicly reachable, use `inline` JWKS mode and paste the keys from `kubectl get --raw /openid/v1/jwks`
2. Create an Anthropic **service account** — this is the identity the workload assumes on the Anthropic side; the access token returned after the token exchange is scoped to it. Its `service_account_id` is included in the OIDC client token exchange request alongside the workload token
3. Create a **federation rule** matching:
   - Subject: `system:serviceaccount:workload-identity-k8s:workload-identity-federation-kubernetes` (where `serviceaccount` refers to the Kubernetes service account name)
   - Audience: `https://api.anthropic.com`

Note the `federation_rule_id`, `organization_id`, and `service_account_id` values — they are needed for the Kubernetes secret in step 4.

## 3. Create the namespace and workload identity

```bash
oc create namespace workload-identity-k8s
oc apply -f deploy/service-account.yml
```

## 4. Create the Anthropic WIF secret

Edit `deploy/secret.yml` with the values from step 2, then apply:

```bash
oc apply -f deploy/secret.yml
```

Or create it directly:

```bash
oc create secret generic anthropic-wif \
    --namespace workload-identity-k8s \
    --from-literal=ANTHROPIC_FEDERATION_RULE_ID=fdrl_... \
    --from-literal=ANTHROPIC_ORGANIZATION_ID=... \
    --from-literal=ANTHROPIC_SERVICE_ACCOUNT_ID=svac_...
```

## 5. Build and deploy

Build the container image using the OpenShift binary build:

```bash
mvn package -DskipTests -Dquarkus.container-image.build=true
```

Deploy the application:

```bash
oc apply -f deploy/deployment.yml
oc apply -f deploy/service.yml
oc apply -f deploy/route.yml
```

Wait for the pod to be ready:

```bash
oc wait pod -l app.kubernetes.io/name=workload-identity-federation-kubernetes -n workload-identity-k8s --for=condition=Ready --timeout=120s
```

## 6. Access the chat UI

Get the route URL:

```bash
oc get route workload-identity-federation-kubernetes -n workload-identity-k8s -o jsonpath='{.spec.host}'
```

Open `https://<route-host>/chat.html` in your browser.

## Deploy files reference

- **`deploy/deployment.yml`** — defines the application pod with a single container. A [projected service account token](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#serviceaccount-token-volume-projection) volume is configured with audience `https://api.anthropic.com` and mounted at `/var/run/secrets/tokens/kubernetes-service-token`. Kubernetes automatically rotates the token before `expirationSeconds` (3600s) elapses. The `anthropic-wif` secret is injected as environment variables (`ANTHROPIC_FEDERATION_RULE_ID`, `ANTHROPIC_ORGANIZATION_ID`, `ANTHROPIC_SERVICE_ACCOUNT_ID`), which are referenced by `application.properties` to configure the OIDC client JWT bearer grant to the Anthropic token endpoint
- **`deploy/service-account.yml`** — the Kubernetes ServiceAccount. Its namespace and name form the token's `sub` claim: `system:serviceaccount:workload-identity-k8s:workload-identity-federation-kubernetes`
- **`deploy/secret.yml`** — holds the Anthropic WIF credentials. These values are passed as environment variables to the application container, where `application.properties` references them as `${ANTHROPIC_FEDERATION_RULE_ID}`, `${ANTHROPIC_ORGANIZATION_ID}`, and `${ANTHROPIC_SERVICE_ACCOUNT_ID}` in the OIDC client grant options sent to the Anthropic token endpoint
- **`deploy/service.yml`** — exposes the application on port 8080
- **`deploy/route.yml`** — creates an edge-terminated OpenShift Route to expose the application externally

## How it works

```
┌─────────────────────────────────────────────────────────┐
│ Pod                                                     │
│                                                         │
│  projected volume:            ┌───────────────────────┐ │
│  /var/run/secrets/tokens/     │ Quarkus application   │ │
│    kubernetes-service-token   │                       │ │
│         │                     │ workload-model-       │ │
│         │  Kubernetes rotates │ auth-provider reads   │ │
│         │  the token          │ token from volume     │ │
│         │  automatically      │         │             │ │
│         └─────────────────────▶         ▼             │ │
│                               │ OIDC Client exchanges │ │
│                               │ token for Anthropic   │ │
│                               │ access token          │ │
│                               │         │             │ │
│                               │         ▼             │ │
│                               │ Calls Claude API with │ │
│                               │ Bearer access token   │ │
│                               └───────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

1. Kubernetes projects a service account token with audience `https://api.anthropic.com` into the pod at `/var/run/secrets/tokens/kubernetes-service-token` and rotates it automatically
2. The `workload-model-auth-provider` extension reads the token from the file
3. The Quarkus OIDC Client exchanges the token for an Anthropic access token using the JWT bearer grant
4. The Anthropic model provider uses the access token in the `Authorization` header to call the Claude API
