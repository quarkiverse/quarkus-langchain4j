import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
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
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._htmlContent = null;
        this._loading = true;
        this._error = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadReport();
    }

    _loadReport() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getExecutionReportHtml()
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
        return html`
            <div class="toolbar">
                <vaadin-button theme="small" @click="${() => this._loadReport()}">
                    Refresh
                </vaadin-button>
                ${this._loading ? html`<span>Loading execution report...</span>` : ''}
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

customElements.define('qwc-agents-executions', QwcAgentsExecutions);
