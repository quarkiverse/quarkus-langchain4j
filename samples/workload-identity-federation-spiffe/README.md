# Workload Identity Federation with SPIFFE

A Quarkus chat application that authenticates to Anthropic Claude using [Workload Identity Federation](https://docs.anthropic.com/en/docs/build-with-claude/workload-identity-federation) with [SPIFFE Identity Provider](https://docs.anthropic.com/en/docs/build-with-claude/workload-identity-federation/spiffe) JWT-SVIDs.

No API keys are stored or distributed — the workload proves its identity through a SPIFFE-issued JWT token, which is exchanged for a short-lived Anthropic access token.

SPIFFE provides a unified identity layer across clusters and infrastructure boundaries, making it a good fit for multi-cluster or hybrid environments.
For single-cluster deployments where no additional identity infrastructure is needed, using Kubernetes projected service tokens is a good alternative. See the `workload-identity-federation-kubernetes` sample.

## Prerequisites

- An OpenShift cluster
- `oc` CLI logged in to the cluster
- An Anthropic organization with Workload Identity Federation access
- `envsubst` (used for placeholder substitution in YAML files)

## 1. Deploy SPIRE

This sample uses [Red Hat Zero Trust Workload Identity Manager](https://www.redhat.com/en/technologies/cloud-computing/openshift/zero-trust-workload-identity-manager)(ZTWIM) to deploy SPIRE on OpenShift. 

See the "Configuring an OpenShift environment" section of the [Federated identity across the hybrid cloud using zero trust workload identity manager](https://developers.redhat.com/articles/2026/05/08/federated-identity-across-hybrid-cloud-using-zero-trust-workload-identity) article for more information about deploying ZTWIM.

### Install the ZTWIM operator

```bash
oc apply -f deploy/ztwim-operator.yml
```

Wait for the Custom Resource Definitions to be established:

```bash
oc wait crd/spireservers.operator.openshift.io --for=condition=established --timeout=120s
```

### Create the ZTWIM instance

Set your OpenShift application domain and apply the ZTWIM instance configuration. This creates the SPIRE Server, SPIRE Agent, SPIRE OIDC Discovery Provider, and SPIFFE CSI Driver (which mounts the SPIRE Workload API socket into application pods so they can request SPIFFE identities):

```bash
export OPENSHIFT_APPS_DOMAIN=<your-openshift-application-domain>
envsubst < deploy/ztwim-instance.yml | oc apply -f -
```

Wait for all components to be ready:

```bash
oc wait pod -l app.kubernetes.io/name=spire-server -n zero-trust-workload-identity-manager --for=condition=Ready --timeout=120s
oc wait pod -l app.kubernetes.io/name=spire-agent -n zero-trust-workload-identity-manager --for=condition=Ready --timeout=120s
```

Verify the OIDC Discovery Provider route:

```bash
oc get route -n zero-trust-workload-identity-manager
```

The OIDC Discovery Provider URL will be `https://spire-spiffe-oidc-discovery-provider.<your-openshift-application-domain>`.

### Identify the issuer URL and subject

When configuring Anthropic (next step), you need two values from the SPIFFE token:

- **Issuer URL** (`iss`): the OIDC Discovery Provider URL configured in `deploy/ztwim-instance.yml` as `jwtIssuer`, e.g. `https://spire-spiffe-oidc-discovery-provider.<your-openshift-application-domain>`
- **Subject** (`sub`): the SPIFFE ID assigned to the workload. It is constructed from the `spiffeIDTemplate` in `deploy/clusterspiffeid.yml`: `spiffe://<trust-domain>/ns/workload-identity-spiffe/sa/workload-identity-federation-spiffe`, where `<trust-domain>` is the `trustDomain` value from `deploy/ztwim-instance.yml` (i.e. your `OPENSHIFT_APPS_DOMAIN`)

You can also verify these values by decoding a token from a running pod:

```bash
oc exec deployment/workload-identity-federation-spiffe -n workload-identity-spiffe -c app \
    -- cat /var/run/secrets/tokens/spiffe-jwt-svid | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```

This will show the JWT payload with `iss` (issuer), `sub` (subject), and `aud` (audience) claims.

## 2. Configure Anthropic

Follow the [Anthropic SPIFFE WIF setup guide](https://docs.anthropic.com/en/docs/build-with-claude/workload-identity-federation/spiffe) to:

1. Register a **federation issuer** using the issuer URL from the previous step (you can use discovery mode)
2. Create an Anthropic **service account** — this is the identity the workload assumes on the Anthropic side; the access token returned after the token exchange is scoped to it. Its `service_account_id` is included in the OIDC client token exchange request alongside the workload token
3. Create a **federation rule** matching:
   - The workload's subject (SPIFFE ID): `spiffe://<trust-domain>/ns/workload-identity-spiffe/sa/workload-identity-federation-spiffe` (where `sa` refers to the Kubernetes service account name)
   - Audience: `https://api.anthropic.com`

Note the `federation_rule_id`, `organization_id`, and `service_account_id` values — they are needed for the Kubernetes secret in step 5.

## 3. Create the namespace and workload identity

```bash
oc create namespace workload-identity-spiffe
oc project workload-identity-spiffe
oc apply -f deploy/service-account.yml
```

## 4. Register the SPIFFE ID

```bash
oc apply -f deploy/clusterspiffeid.yml
```

This creates a `ClusterSPIFFEID` that tells SPIRE to issue JWT-SVIDs for pods matching the `workload-identity-federation-spiffe` label in the `workload-identity-spiffe` namespace. The `spiffeIDTemplate` in `deploy/clusterspiffeid.yml` defines the SPIFFE ID pattern using the pod's namespace and Kubernetes service account name, for example: `spiffe://<trust-domain>/ns/workload-identity-spiffe/sa/workload-identity-federation-spiffe`.

## 5. Create the Anthropic WIF secret

Edit `deploy/secret.yml` with the values from step 2, then apply:

```bash
oc apply -f deploy/secret.yml
```

Or create it directly:

```bash
oc create secret generic anthropic-wif \
    --namespace workload-identity-spiffe \
    --from-literal=ANTHROPIC_FEDERATION_RULE_ID=fdrl_... \
    --from-literal=ANTHROPIC_ORGANIZATION_ID=... \
    --from-literal=ANTHROPIC_SERVICE_ACCOUNT_ID=svac_...
```

## 6. Build and deploy

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
oc wait pod -l app.kubernetes.io/name=workload-identity-federation-spiffe -n workload-identity-spiffe --for=condition=Ready --timeout=120s
```

## 7. Access the chat UI

Get the route URL:

```bash
oc get route workload-identity-federation-spiffe -n workload-identity-spiffe -o jsonpath='{.spec.host}'
```

Open `https://<route-host>/chat.html` in your browser and type a message such as `What is your name?` to verify the application can authenticate and chat with the model.
