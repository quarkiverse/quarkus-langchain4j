import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/select';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/progress-bar';

import { agents } from 'build-time-data';

export class QwcAgentsTesting extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
            padding: 15px;
            gap: 15px;
            overflow: auto;
        }
        .controls {
            display: flex;
            gap: 10px;
            align-items: flex-end;
            flex-wrap: wrap;
        }
        .params-section {
            display: flex;
            flex-direction: column;
            gap: 8px;
            padding: 10px;
            background: var(--lumo-contrast-5pct);
            border-radius: 4px;
        }
        .params-section h4 {
            margin: 0 0 5px 0;
        }
        .result-section {
            flex: 1;
            min-height: 100px;
        }
        .result-success {
            background: var(--lumo-success-color-10pct);
            border: 1px solid var(--lumo-success-color);
            border-radius: 4px;
            padding: 15px;
            white-space: pre-wrap;
            word-break: break-word;
            font-family: monospace;
            font-size: 0.9em;
            overflow: auto;
        }
        .result-error {
            background: var(--lumo-error-color-10pct);
            border: 1px solid var(--lumo-error-color);
            border-radius: 4px;
            padding: 15px;
            white-space: pre-wrap;
            word-break: break-word;
            font-family: monospace;
            font-size: 0.9em;
            color: var(--lumo-error-text-color);
            overflow: auto;
        }
        .method-info {
            font-size: 0.85em;
            color: var(--lumo-secondary-text-color);
            margin-top: 4px;
        }
        .no-agents {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        _agents: { state: true },
        _selectedAgent: { state: true },
        _selectedMethod: { state: true },
        _paramValues: { state: true },
        _result: { state: true },
        _invoking: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._agents = agents || [];
        this._selectedAgent = null;
        this._selectedMethod = null;
        this._paramValues = {};
        this._result = null;
        this._invoking = false;
    }

    _onAgentSelected(e) {
        const className = e.target.value;
        this._selectedAgent = this._agents.find(a => a.className === className) || null;
        this._selectedMethod = null;
        this._paramValues = {};
        this._result = null;

        if (this._selectedAgent && this._selectedAgent.methods && this._selectedAgent.methods.length > 0) {
            this._selectMethod(this._selectedAgent.methods[0]);
        }
    }

    _selectMethod(methodSig) {
        this._selectedMethod = methodSig;
        this._paramValues = {};
        this._result = null;
    }

    _onParamInput(paramName, value) {
        this._paramValues = { ...this._paramValues, [paramName]: value };
    }

    _invoke() {
        if (!this._selectedAgent || !this._selectedMethod) return;

        const methodName = this._extractMethodName(this._selectedMethod);
        this._invoking = true;
        this._result = null;

        this.jsonRpc.invokeAgent({
            agentClassName: this._selectedAgent.className,
            methodName: methodName,
            inputJson: JSON.stringify(this._paramValues),
        })
        .then(response => {
            this._result = response.result;
            this._invoking = false;
        })
        .catch(error => {
            this._result = { success: false, error: String(error) };
            this._invoking = false;
        });
    }

    _extractMethodName(methodSig) {
        const match = methodSig.match(/\s(\w+)\(/);
        return match ? match[1] : methodSig;
    }

    _extractParams(methodSig) {
        const match = methodSig.match(/\(([^)]*)\)/);
        if (!match || !match[1].trim()) return [];
        return match[1].split(',').map(p => {
            const parts = p.trim().split(/\s+/);
            return {
                type: parts[0] || 'String',
                name: parts[1] || parts[0],
            };
        }).filter(p =>
            p.type !== 'AgenticScope' &&
            p.type !== 'MemoryId'
        );
    }

    render() {
        if (this._agents.length === 0) {
            return html`<div class="no-agents">No agents detected.</div>`;
        }

        const sorted = [...this._agents].sort((a, b) => {
            if (a.rootAgent !== b.rootAgent) return a.rootAgent ? -1 : 1;
            return a.simpleName.localeCompare(b.simpleName);
        });
        const agentItems = sorted.map(a => ({
            label: `${a.simpleName} (${a.agentType})${a.rootAgent ? '' : ' [sub-agent]'}`,
            value: a.className,
        }));

        return html`
            <div class="controls">
                <vaadin-select
                    label="Agent"
                    .items="${agentItems}"
                    @value-changed="${this._onAgentSelected}">
                </vaadin-select>
            </div>

            ${this._selectedAgent ? html`
                ${this._selectedAgent.methods && this._selectedAgent.methods.length > 1 ? html`
                    <div class="controls">
                        <vaadin-select
                            label="Method"
                            .items="${this._selectedAgent.methods.map(m => ({ label: m, value: m }))}"
                            .value="${this._selectedMethod}"
                            @value-changed="${e => this._selectMethod(e.target.value)}">
                        </vaadin-select>
                    </div>
                ` : ''}

                ${this._selectedMethod ? html`
                    <div class="method-info">
                        Signature: <code>${this._selectedMethod}</code>
                    </div>

                    ${this._renderParams()}

                    <div class="controls">
                        <vaadin-button theme="primary" @click="${this._invoke}"
                            ?disabled="${this._invoking}">
                            ${this._invoking ? 'Invoking...' : 'Invoke'}
                        </vaadin-button>
                    </div>

                    ${this._invoking ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : ''}

                    ${this._result ? this._renderResult() : ''}
                ` : ''}
            ` : ''}
        `;
    }

    _renderParams() {
        const params = this._extractParams(this._selectedMethod);
        if (params.length === 0) {
            return html`<div class="method-info">This method takes no user-provided parameters.</div>`;
        }
        return html`
            <div class="params-section">
                <h4>Parameters</h4>
                ${params.map(p => html`
                    <vaadin-text-field
                        label="${p.name} (${p.type})"
                        .value="${this._paramValues[p.name] || ''}"
                        @input="${e => this._onParamInput(p.name, e.target.value)}">
                    </vaadin-text-field>
                `)}
            </div>
        `;
    }

    _renderResult() {
        if (this._result.success) {
            return html`
                <div class="result-section">
                    <h4>Result</h4>
                    <div class="result-success">${this._result.result}</div>
                </div>
            `;
        } else {
            return html`
                <div class="result-section">
                    <h4>Error</h4>
                    <div class="result-error">${this._result.error}</div>
                </div>
            `;
        }
    }
}

customElements.define('qwc-agents-testing', QwcAgentsTesting);
