import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/progress-bar';

export class QwcAgentsTopology extends LitElement {

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
        .iframe-container {
            flex: 1;
            padding: 0 15px 15px 15px;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: 4px;
            background: var(--lumo-base-color);
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        _htmlContent: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _agentEntries: { state: true },
        _selectedIndex: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._htmlContent = null;
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
                this._loadTopology();
            })
            .catch(() => this._loadTopology());
    }

    _onAgentSelected(e) {
        this._selectedIndex = parseInt(e.target.value, 10);
        this._loadTopology();
    }

    _loadTopology() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getTopologyHtml({ index: this._selectedIndex })
            .then(response => {
                this._htmlContent = response.result;
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
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
                <vaadin-button theme="small" @click="${() => this._loadTopology()}">
                    Refresh
                </vaadin-button>
                ${this._loading ? html`<span>Loading topology...</span>` : ''}
            </div>
            ${this._loading ? html`
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            ` : this._error ? html`
                <div class="placeholder">${this._error}</div>
            ` : html`
                <div class="iframe-container">
                    <iframe .srcdoc="${this._htmlContent}" sandbox="allow-scripts"></iframe>
                </div>
            `}
        `;
    }
}

customElements.define('qwc-agents-topology', QwcAgentsTopology);
