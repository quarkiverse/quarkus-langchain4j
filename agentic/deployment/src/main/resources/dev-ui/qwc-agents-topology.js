import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { themeState } from 'theme-state';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/progress-bar';

const MERMAID_CDN = 'https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js';

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
        .diagram-container {
            flex: 1;
            padding: 0 15px 15px 15px;
            overflow: auto;
        }
        .mermaid-target {
            display: flex;
            justify-content: center;
        }
        .mermaid-target svg {
            max-width: 100%;
            height: auto;
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        _mermaid: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _agentEntries: { state: true },
        _selectedIndex: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._mermaid = null;
        this._loading = true;
        this._error = null;
        this._agentEntries = [];
        this._selectedIndex = 0;
    }

    connectedCallback() {
        super.connectedCallback();
        if (!window.mermaid) {
            const script = document.createElement('script');
            script.src = MERMAID_CDN;
            script.onload = () => {
                this._initializeMermaid();
                this._loadAgentEntries();
            };
            script.onerror = () => {
                this._error = 'Failed to load the diagram library.';
                this._loading = false;
            };
            document.head.appendChild(script);
        } else {
            this._initializeMermaid();
            this._loadAgentEntries();
        }
    }

    _initializeMermaid() {
        window.mermaid.initialize({
            startOnLoad: false,
            theme: themeState.theme.name === 'dark' ? 'dark' : 'default',
            fontFamily: 'var(--lumo-font-family)',
            fontSize: 12,
            flowchart: {
                useMaxWidth: true,
                htmlLabels: true,
            },
        });
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
        this.jsonRpc.getTopologyMermaid({ index: this._selectedIndex })
            .then(response => {
                const result = response.result;
                if (result && result.error) {
                    this._error = result.error;
                    this._mermaid = null;
                } else {
                    this._mermaid = result ? result.mermaid : null;
                }
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    async updated(changed) {
        super.updated?.(changed);
        if (this._mermaid && window.mermaid) {
            const target = this.shadowRoot?.querySelector('.mermaid-target');
            if (target && target.dataset.rendered !== this._mermaid) {
                try {
                    const { svg } = await window.mermaid.render('agents-topology-svg', this._mermaid);
                    target.innerHTML = svg;
                    target.dataset.rendered = this._mermaid;
                } catch (error) {
                    target.innerHTML = '';
                    this._error = 'Error rendering diagram: ' + (error.message || error);
                }
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
                <vaadin-button theme="small" @click="${() => this._loadTopology()}">
                    Refresh
                </vaadin-button>
                ${this._loading ? html`<span>Loading topology...</span>` : ''}
            </div>
            ${this._loading ? html`
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            ` : this._error ? html`
                <div class="placeholder">${this._error}</div>
            ` : this._mermaid ? html`
                <div class="diagram-container">
                    <div class="mermaid-target"></div>
                </div>
            ` : html`
                <div class="placeholder">No topology available.</div>
            `}
        `;
    }
}

customElements.define('qwc-agents-topology', QwcAgentsTopology);
