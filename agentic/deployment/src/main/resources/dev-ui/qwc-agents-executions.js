import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/grid';
import '@vaadin/progress-bar';

export class QwcAgentsExecutions extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
        }
        .toolbar {
            padding: 10px 15px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .grid-container {
            flex: 1;
            padding: 0 15px 15px 15px;
        }
        vaadin-grid {
            height: 100%;
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
        .error-row {
            color: var(--lumo-error-text-color);
        }
    `;

    static properties = {
        _executions: { state: true },
        _flatRows: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _agentEntries: { state: true },
        _selectedIndex: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._executions = [];
        this._flatRows = [];
        this._loading = true;
        this._error = null;
        this._agentEntries = [];
        this._selectedIndex = 0;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadAgentEntries();
    }

    _loadAgentEntries() {
        this.jsonRpc.getRootAgentEntries()
            .then(response => {
                this._agentEntries = response.result || [];
                this._selectedIndex = this._agentEntries.length > 0 ? this._agentEntries[0].index : 0;
                this._loadReport();
            })
            .catch(() => this._loadReport());
    }

    _onAgentSelected(e) {
        this._selectedIndex = parseInt(e.target.value, 10);
        this._loadReport();
    }

    _loadReport() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getExecutionReportJson({ index: this._selectedIndex })
            .then(response => {
                const data = response.result;
                this._executions = data.executions || [];
                this._flatRows = this._flatten(this._executions);
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    _flatten(executions) {
        const rows = [];
        for (const exec of executions) {
            this._flattenInvocation(exec.topLevel, 0, exec.status, exec.memoryId, rows);
        }
        return rows;
    }

    _flattenInvocation(inv, level, status, memoryId, rows) {
        if (!inv) return;
        rows.push({
            agentName: inv.agentName,
            status: inv.status || status,
            duration: inv.duration != null ? inv.duration + ' ms' : 'in progress',
            tokenCount: inv.tokenCount || 0,
            iterationIndex: inv.iterationIndex >= 0 ? inv.iterationIndex : '',
            level,
            memoryId,
        });
        if (inv.nestedInvocations) {
            for (const nested of inv.nestedInvocations) {
                this._flattenInvocation(nested, level + 1, status, memoryId, rows);
            }
        }
    }

    render() {
        const agentItems = this._agentEntries.map(e => ({
            label: e.name,
            value: String(e.index),
        }));

        return html`
            <div class="toolbar">
                ${agentItems.length > 1 ? html`
                    <vaadin-select
                        label="Root Agent"
                        .items="${agentItems}"
                        .value="${String(this._selectedIndex)}"
                        @value-changed="${this._onAgentSelected}">
                    </vaadin-select>
                ` : ''}
                <vaadin-button theme="small" @click="${() => this._loadReport()}">
                    Refresh
                </vaadin-button>
            </div>
            ${this._loading ? html`
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            ` : this._error ? html`
                <div class="placeholder">${this._error}</div>
            ` : this._flatRows.length === 0 ? html`
                <div class="placeholder">No execution data. Invoke an agent first.</div>
            ` : html`
                <div class="grid-container">
                    <vaadin-grid .items="${this._flatRows}" theme="compact row-stripes">
                        <vaadin-grid-column header="Agent" path="agentName"></vaadin-grid-column>
                        <vaadin-grid-column header="Status" path="status" width="100px" flex-grow="0"></vaadin-grid-column>
                        <vaadin-grid-column header="Duration" path="duration" width="120px" flex-grow="0"></vaadin-grid-column>
                        <vaadin-grid-column header="Tokens" path="tokenCount" width="80px" flex-grow="0"></vaadin-grid-column>
                        <vaadin-grid-column header="Iteration" path="iterationIndex" width="80px" flex-grow="0"></vaadin-grid-column>
                        <vaadin-grid-column header="Level" path="level" width="60px" flex-grow="0"></vaadin-grid-column>
                    </vaadin-grid>
                </div>
            `}
        `;
    }
}

customElements.define('qwc-agents-executions', QwcAgentsExecutions);
