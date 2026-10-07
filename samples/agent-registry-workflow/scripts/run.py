#!/usr/bin/env python3
"""Run the sample, or exercise its real Registry/A2A/MCP/LLM flow with --smoke."""
import argparse
import http.client
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = "http://localhost:8180/apis/registry/v3"
ORCHESTRATOR = "http://localhost:11020/workflow"


def request(url, data=None, method=None, expected=200, content_type="application/json"):
    body = data.encode() if isinstance(data, str) else json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method, headers={"Content-Type": content_type})
    try:
        with urllib.request.urlopen(req, timeout=240) as response:
            status, raw = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read().decode()
    if status != expected:
        raise RuntimeError(f"{req.get_method()} {url}: expected {expected}, got {status}: {raw[:1000]}")
    return json.loads(raw) if raw else None


def wait_for(url, processes, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(process.poll() is not None for process in processes):
            raise RuntimeError("An application exited during startup; inspect target/run/*.log")
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (OSError, http.client.HTTPException):
            pass
        time.sleep(0.5)
    raise TimeoutError(f"Timed out waiting for {url}; inspect target/run/*.log")


def seed_weather():
    # The current extension discovers MCP_TOOL artifacts with these two connection labels.
    # This explicit registration is not the planned Registry-side tools/list importer.
    tool = {
        "name": "getWeather",
        "description": "Fictional sample weather for Amsterdam, Paris or Madrid",
        "inputSchema": {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]},
    }
    request(REGISTRY + "/groups/weather-tools/artifacts", {
        "artifactId": "weather", "artifactType": "MCP_TOOL", "name": "weather",
        "description": "Fictional sample weather for Amsterdam, Paris or Madrid",
        "labels": {"mcp-server-url": "http://localhost:11040/mcp", "mcp-transport-type": "streamable-http"},
        "firstVersion": {"version": "1.0.0", "content": {"contentType": "application/json", "content": json.dumps(tool)}},
    }, expected=200)


def check_governance():
    base = REGISTRY + "/groups/default/artifacts/summarizer"
    before = request(base + "/versions/branch=latest/content")
    rules = request(base + "/rules")
    if "COMPATIBILITY" not in rules:
        request(base + "/rules", {"ruleType": "COMPATIBILITY", "config": "BACKWARD"}, expected=204)
    changed = dict(before, skills=[])
    rejection = request(base + "/versions", {
        "version": "breaking-demo", "content": {"contentType": "application/json", "content": json.dumps(changed)},
    }, expected=400)
    if "skill" not in json.dumps(rejection).lower():
        raise AssertionError(f"Expected a removed-skill compatibility violation: {rejection}")
    request(base + "/versions/breaking-demo", expected=404)
    if request(base + "/versions/branch=latest/content") != before:
        raise AssertionError("Rejected change replaced accepted content")
    # Also exercise an accepted additive change, not just a failing request.
    suffix = str(time.time_ns())
    added = dict(before["skills"][0], id="additional-summary-" + suffix, name="Additional summary")
    compatible = dict(before, skills=before["skills"] + [added])
    request(base + "/versions", {
        "version": "compatible-demo-" + suffix,
        "content": {"contentType": "application/json", "content": json.dumps(compatible)},
    })
    current = request(base + "/versions/branch=latest/content")
    if len(current["skills"]) != len(before["skills"]) + 1:
        raise AssertionError("Compatible skill addition was not persisted")
    print("PASS: removed skill rejected (400), accepted content retained, additive change accepted", flush=True)


def translator_state(state):
    base = REGISTRY + "/groups/default/artifacts/translator/versions/branch=latest"
    request(base + "/state", {"state": state}, method="PUT", expected=204)
    print(f"Translator contract is now {state}", flush=True)


def briefing(expected=200):
    return request(ORCHESTRATOR + "/briefing", {
        "city": "Amsterdam", "context": "Visitors will walk through the city and meet colleagues to discuss open standards."
    }, expected=expected)


def smoke():
    agents = request(ORCHESTRATOR + "/agents")
    if set(agents) != {"summarizer", "translator"}:
        raise AssertionError(f"Expected two discovered agents, got {agents}")
    print("PASS: both agents discovered through ApicurioAgentsRegistry", flush=True)
    result = request(ORCHESTRATOR + "/summarize-and-translate",
                     "Amsterdam has canals and many bicycles. Open standards let independently built services communicate.",
                     content_type="text/plain")
    if not result["summary"].strip() or not result["translation"].strip():
        raise AssertionError(f"Both remote agents must return text: {result}")
    print("A2A result:", json.dumps(result, ensure_ascii=False), flush=True)
    print("PASS: real summarize → translate A2A calls completed (language quality requires human evaluation)", flush=True)
    weather = request(ORCHESTRATOR + "/weather", "What is the weather in Amsterdam?",
                      content_type="text/plain")
    if "amsterdam" not in weather["answer"].lower() or "16" not in weather["answer"]:
        raise AssertionError(f"Model did not return the tool's Amsterdam fixture: {weather}")
    if "Sample MCP getWeather invoked: Amsterdam: 16 C, light rain" not in (ROOT / "target/run/weather.log").read_text():
        raise AssertionError("The answer was not backed by an observed Weather MCP server invocation")
    print("MCP result:", weather["answer"], flush=True)
    print("PASS: registered endpoint connected and getWeather actually executed on the MCP server", flush=True)
    result = briefing()
    steps = result["steps"]
    if [s["operation"] for s in steps] != ["MCP weather", "A2A summarize", "A2A translate"]:
        raise AssertionError(f"Incomplete connected workflow: {result}")
    if "16 C" not in steps[0]["output"] or steps[0]["output"] not in steps[1]["input"]:
        raise AssertionError("The summarizer did not receive the actual MCP weather result")
    if steps[2]["input"] != steps[1]["output"] or not result["result"].strip():
        raise AssertionError("The translator did not receive the actual summary")
    if len(result["contracts"]) != 3 or any(c["state"] != "ENABLED" for c in result["contracts"]):
        raise AssertionError(f"Expected three enabled contracts: {result['contracts']}")
    print("PASS: connected MCP → A2A summary → A2A translation", json.dumps(result, ensure_ascii=False), flush=True)
    check_governance()
    briefing()  # The rejected update did not prevent the consumer from using accepted contracts.
    log = ROOT / "target/run/weather.log"
    before = log.read_text().count("Sample MCP getWeather invoked")
    try:
        translator_state("DEPRECATED")
        blocked = briefing(expected=503)
        if blocked.get("stage") != "contract-check" or "DEPRECATED" not in blocked.get("error", ""):
            raise AssertionError(f"Expected an explicit lifecycle rejection: {blocked}")
        if log.read_text().count("Sample MCP getWeather invoked") != before:
            raise AssertionError("A blocked briefing still invoked MCP")
    finally:
        translator_state("ENABLED")
    briefing()
    print("PASS: rejected change retains working flow; deprecation blocks before calls; restore recovers", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--smoke", action="store_true", help="run live assertions and stop all sample services")
    modes.add_argument("--governance", action="store_true", help="exercise compatible/incompatible updates on an already-running sample")
    modes.add_argument("--deprecate", action="store_true", help="deprecate the running sample's translator contract")
    modes.add_argument("--restore", action="store_true", help="restore the running sample's translator contract")
    args = parser.parse_args()
    if args.governance or args.deprecate or args.restore:
        if not subprocess.check_output(["docker", "compose", "-p", "langchain4j-agent-workflow", "-f",
                                        str(ROOT / "compose.yaml"), "ps", "-q", "registry"], text=True).strip():
            raise RuntimeError("Start this sample's isolated stack first")
        if args.governance:
            check_governance()
        else:
            translator_state("DEPRECATED" if args.deprecate else "ENABLED")
        return
    model = os.environ.get("OLLAMA_MODEL", "qwen2.5:7b")
    ollama = os.environ.get("OLLAMA_URL", "http://localhost:11434").rstrip("/")
    tags = request(ollama + "/api/tags")
    if model not in {entry["name"] for entry in tags.get("models", [])}:
        raise RuntimeError(f"Model {model} is not installed. Run: ollama pull {model}")
    for port in (8180, 11010, 11020, 11030, 11040):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            probe.bind(("127.0.0.1", port))
    jars = {module: ROOT / module / "target/quarkus-app/quarkus-run.jar"
            for module in ("text-agent", "weather-server", "orchestrator")}
    for jar in jars.values():
        if not jar.exists():
            raise RuntimeError(f"Build the sample first: missing {jar}")
    logs = ROOT / "target/run"
    logs.mkdir(parents=True, exist_ok=True)
    processes, files = [], []
    compose = ["docker", "compose", "-p", "langchain4j-agent-workflow", "-f", str(ROOT / "compose.yaml")]
    # Do not reuse/reset another invocation's Registry or data.
    if subprocess.check_output(compose + ["ps", "-aq"], text=True).strip():
        raise RuntimeError("Sample containers already exist; stop that run before starting another")
    try:
        subprocess.run(compose + ["up", "-d"], check=True)
        wait_for("http://localhost:8180/apis/registry/v3/system/info", processes)
        for name, module, properties in (
            ("summarizer", "text-agent", []),
            ("translator", "text-agent", ["-Dquarkus.profile=translator"]),
            ("weather", "weather-server", []),
            ("orchestrator", "orchestrator", []),
        ):
            log = (logs / f"{name}.log").open("w")
            files.append(log)
            # Explicit endpoints keep the isolated runner independent of inherited demo settings.
            environment = dict(os.environ, REGISTRY_URL=REGISTRY, OLLAMA_URL=ollama, OLLAMA_MODEL=model)
            environment.pop("AGENT_URL", None)
            processes.append(subprocess.Popen(["java", *properties, "-jar", str(jars[module])],
                                               env=environment, stdout=log, stderr=subprocess.STDOUT))
        for port in (11010, 11030, 11040, 11020):
            wait_for(f"http://localhost:{port}/q/health/ready", processes)
        seed_weather()
        print(f"Ready: {ORCHESTRATOR}/agents; logs: {logs}", flush=True)
        if args.smoke:
            smoke()
        else:
            print("Use the README requests. Ctrl+C stops the sample and its ephemeral Registry.", flush=True)
            while all(process.poll() is None for process in processes):
                time.sleep(1)
            raise RuntimeError("An application exited; inspect target/run/*.log")
    except KeyboardInterrupt:
        pass
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
        for process in processes:
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for log in files:
            log.close()
        subprocess.run(compose + ["down"], check=True)


if __name__ == "__main__":
    main()
